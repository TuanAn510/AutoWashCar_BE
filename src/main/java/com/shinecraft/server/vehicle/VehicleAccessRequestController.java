package com.shinecraft.server.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.FileStorageService;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.notification.NotificationService;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VehicleAccessRequestController {
    /** Loại tài liệu minh chứng: cho hãng/dòng xe (luồng combined). */
    static final String DOC_TYPE_BRAND_MODEL = "BRAND_MODEL";
    /** Loại tài liệu minh chứng: cho biển số/quyền sử dụng. */
    static final String DOC_TYPE_PLATE = "PLATE";

    private final AuthService authService;
    private final VehicleAccessRequestRepository requestRepository;
    private final VehicleAccessRequestDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleBrandRepository brandRepository;
    private final VehicleModelRepository modelRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public VehicleAccessRequestController(
            AuthService authService,
            VehicleAccessRequestRepository requestRepository,
            VehicleAccessRequestDocumentRepository documentRepository,
            VehicleRepository vehicleRepository,
            VehicleBrandRepository brandRepository,
            VehicleModelRepository modelRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService) {
        this.authService = authService;
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @PostMapping(path = "/api/vehicle-access-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> create(@ModelAttribute VehicleAccessRequestForm request) {
        User requester = authService.currentUser();
        String licensePlate = LicensePlateNormalizer.normalize(request.getLicensePlate());
        if (requestRepository.existsByRequesterIdAndLicensePlateAndStatus(
                requester.getId(), licensePlate, VehicleAccessRequestStatus.PENDING)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Xe này đã có yêu cầu xác minh đang chờ xử lý.");
        }
        VehicleAccessRequest accessRequest = new VehicleAccessRequest();
        accessRequest.setRequester(requester);
        accessRequest.setLicensePlate(licensePlate);
        accessRequest.setRelationship(request.getRelationship());
        accessRequest.setNote(request.getNote());
        accessRequest.setSuggestedBrandName(request.getSuggestedBrandName());
        accessRequest.setSuggestedModelName(request.getSuggestedModelName());
        accessRequest.setCatalogBrandName(request.getCatalogBrandName());
        accessRequest.setCatalogModelName(request.getCatalogModelName());
        vehicleRepository.findByLicensePlateAndIsActiveTrue(licensePlate).ifPresent(accessRequest::setVehicle);
        VehicleAccessRequest saved = requestRepository.save(accessRequest);
        // Luồng combined (trùng biển + "Khác" hãng/dòng): minh chứng được tách
        // thành 2 nhóm. Ngược lại dùng documents chung làm minh chứng biển số.
        if (hasText(request.getSuggestedBrandName()) || hasText(request.getSuggestedModelName())) {
            saveDocuments(saved, request.getBrandModelDocuments(), DOC_TYPE_BRAND_MODEL);
            saveDocuments(saved, request.getPlateDocuments(), DOC_TYPE_PLATE);
        } else {
            saveDocuments(saved, request.getDocuments(), DOC_TYPE_PLATE);
        }
        notificationService.notifyAdmins(
                "VEHICLE_REQUEST_CREATED",
                "Có yêu cầu xác minh xe",
                "Khách gửi yêu cầu xác minh xe biển số " + licensePlate + ".",
                "VEHICLE_REQUEST",
                saved.getId());
        return ApiResponse.ok(
                "Vehicle access request created successfully",
                toResponse(saved));
    }

    @PostMapping(path = "/api/vehicle-access-requests/brand-model", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> resubmitBrandModel(@ModelAttribute BrandModelResubmitForm form) {
        User requester = authService.currentUser();
        String licensePlate = LicensePlateNormalizer.normalize(form.getLicensePlate());
        Vehicle vehicle = null;
        if (form.getVehicleId() != null) {
            // Update path: the vehicle already exists and only its brand/model is re-verified.
            vehicle = vehicleRepository
                    .findById(form.getVehicleId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found"));
            if (vehicle.getCustomer() == null || !vehicle.getCustomer().getId().equals(requester.getId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Vehicle not found");
            }
            if (requestRepository.existsByVehicleIdAndRequestTypeAndStatus(
                    vehicle.getId(),
                    VehicleAccessRequestType.BRAND_MODEL_VERIFICATION,
                    VehicleAccessRequestStatus.PENDING)) {
                throw new ApiException(HttpStatus.CONFLICT, "A brand/model verification request is already pending");
            }
            licensePlate = vehicle.getLicensePlate();
        } else if (requestRepository.existsByRequesterIdAndLicensePlateAndRequestTypeAndStatus(
                requester.getId(),
                licensePlate,
                VehicleAccessRequestType.BRAND_MODEL_VERIFICATION,
                VehicleAccessRequestStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "A brand/model verification request is already pending");
        }

        VehicleAccessRequest accessRequest = new VehicleAccessRequest();
        accessRequest.setRequester(requester);
        accessRequest.setVehicle(vehicle);
        accessRequest.setLicensePlate(licensePlate);
        accessRequest.setRelationship("BRAND_MODEL_VERIFICATION");
        accessRequest.setNote(form.getNote());
        accessRequest.setRequestType(VehicleAccessRequestType.BRAND_MODEL_VERIFICATION);
        accessRequest.setSuggestedBrandName(
                hasText(form.getSuggestedBrandName())
                        ? form.getSuggestedBrandName().trim()
                        : (vehicle != null ? vehicle.getBrand() : null));
        accessRequest.setSuggestedModelName(
                hasText(form.getSuggestedModelName())
                        ? form.getSuggestedModelName().trim()
                        : (vehicle != null ? vehicle.getModel() : null));
        accessRequest.setCarType(vehicle != null ? vehicle.getCarType() : form.getCarType());
        accessRequest.setManufactureYear(vehicle != null ? vehicle.getManufactureYear() : form.getManufactureYear());
        VehicleAccessRequest saved = requestRepository.save(accessRequest);
        saveDocuments(saved, form.getDocuments(), DOC_TYPE_PLATE);
        if (vehicle != null) {
            vehicle.setVerificationStatus(VehicleVerificationStatus.PENDING);
            vehicleRepository.save(vehicle);
        }
        notificationService.notifyAdmins(
                "VEHICLE_REQUEST_CREATED",
                "Có yêu cầu xác minh xe",
                "Khách gửi yêu cầu xác minh xe biển số " + licensePlate + ".",
                "VEHICLE_REQUEST",
                saved.getId());
        return ApiResponse.ok("Vehicle brand/model verification resubmitted successfully", toResponse(saved));
    }

    @GetMapping("/api/vehicle-access-requests/me")
    @Transactional(readOnly = true)
    ApiListResponse<VehicleAccessRequestResponse> mine() {
        return ApiListResponse.ok(
                "Vehicle access requests retrieved successfully",
                requestRepository.findByRequesterOrderByCreatedAtDesc(authService.currentUser()).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @GetMapping("/api/vehicle-access-requests")
    @Transactional(readOnly = true)
    ApiListResponse<VehicleAccessRequestResponse> all(@RequestParam(required = false) String status) {
        List<VehicleAccessRequest> requests = status == null || status.isBlank()
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        return ApiListResponse.ok(
                "Vehicle access requests retrieved successfully",
                requests.stream()
                        .map(this::toResponse)
                        .toList());
    }

    @PatchMapping("/api/vehicle-access-requests/{id}/approve")
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.ok("Vehicle access request approved successfully", review(id, VehicleAccessRequestStatus.APPROVED, request));
    }

    @PatchMapping("/api/vehicle-access-requests/{id}/reject")
    @Transactional
    ApiResponse<VehicleAccessRequestResponse> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.ok("Vehicle access request rejected successfully", review(id, VehicleAccessRequestStatus.REJECTED, request));
    }

    private VehicleAccessRequestResponse review(Long id, VehicleAccessRequestStatus status, ReviewRequest request) {
        VehicleAccessRequest accessRequest = requestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vehicle access request not found"));
        accessRequest.setStatus(status);
        accessRequest.setReviewNote(request == null ? null : request.reviewNote());
        accessRequest.setReviewedAt(LocalDateTime.now());

        if (accessRequest.getRequestType() == VehicleAccessRequestType.BRAND_MODEL_VERIFICATION) {
            reviewBrandModelVerification(accessRequest, status);
        } else if (status == VehicleAccessRequestStatus.APPROVED && accessRequest.getVehicle() != null) {
            // Yêu cầu truy cập xe trùng biển: lấy xe xác minh mới nhất thêm cho
            // khách, rồi xóa (soft) xe cũ mang cùng biển số để mỗi biển chỉ còn
            // một xe active. Phải xóa xe cũ TRƯỚC khi chèn xe mới vì DB có index
            // unique filtered (license_plate) WHERE is_active=1.
            Vehicle oldDuplicate = accessRequest.getVehicle();
            softDeleteDuplicate(oldDuplicate);
            Vehicle keeper = buildKeeper(accessRequest, oldDuplicate);
            vehicleRepository.saveAndFlush(keeper);
            oldDuplicate.setReplacedByVehicleId(keeper.getId());
            vehicleRepository.save(oldDuplicate);
            accessRequest.setVehicle(keeper);
        }

        VehicleAccessRequest saved = requestRepository.save(accessRequest);
        notificationService.notify(
                saved.getRequester(),
                status == VehicleAccessRequestStatus.APPROVED
                        ? "VEHICLE_REQUEST_APPROVED"
                        : "VEHICLE_REQUEST_REJECTED",
                status == VehicleAccessRequestStatus.APPROVED
                        ? "Yêu cầu xác minh xe đã được duyệt"
                        : "Yêu cầu xác minh xe bị từ chối",
                status == VehicleAccessRequestStatus.APPROVED
                        ? "Yêu cầu xác minh xe biển số " + saved.getLicensePlate() + " đã được duyệt."
                        : "Yêu cầu xác minh xe biển số " + saved.getLicensePlate() + " bị từ chối.",
                "VEHICLE_REQUEST",
                saved.getId());
        return toResponse(saved);
    }

    /** Tạo xe MỚI nhất (đã xác minh) cho người request từ một yêu cầu truy cập
     *  xe trùng biển. Hãng/dòng lấy từ đề xuất nếu có (luồng "Khác"), ngược lại
     *  kế thừa từ xe cũ trùng biển (luồng chọn hãng/dòng có sẵn). */
    private Vehicle buildKeeper(VehicleAccessRequest accessRequest, Vehicle oldDuplicate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(accessRequest.getRequester());
        vehicle.setLicensePlate(accessRequest.getLicensePlate());
        vehicle.setCarType(hasText(accessRequest.getCarType())
                ? accessRequest.getCarType().trim()
                : (hasText(oldDuplicate.getCarType()) ? oldDuplicate.getCarType() : "sedan"));
        vehicle.setManufactureYear(accessRequest.getManufactureYear() != null
                ? accessRequest.getManufactureYear()
                : oldDuplicate.getManufactureYear());
        vehicle.setColor(oldDuplicate.getColor());
        vehicle.setOwnershipStartAt(LocalDateTime.now());
        // Query chạy khi xe mới chưa được persist nhưng accessRequest vẫn trỏ
        // tới xe cũ (đã persist) nên không gây lỗi transient reference.
        VehicleBrand brand = resolveOrCreateBrand(accessRequest, vehicle);
        VehicleModel model = resolveOrCreateModel(accessRequest, vehicle, brand);
        if (brand != null) {
            vehicle.setBrandRef(brand);
            vehicle.setBrand(brand.getName());
        } else if (hasText(oldDuplicate.getBrand())) {
            vehicle.setBrand(oldDuplicate.getBrand());
        }
        if (model != null) {
            vehicle.setModelRef(model);
            vehicle.setModel(model.getName());
        } else if (hasText(oldDuplicate.getModel())) {
            vehicle.setModel(oldDuplicate.getModel());
        }
        vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
        vehicle.setVerifiedAt(LocalDateTime.now());
        vehicle.setVerifiedBy(authService.currentUser().getId());
        vehicle.setVerificationNote(accessRequest.getReviewNote());
        return vehicle;
    }

    /** Soft-delete xe cũ trùng biển và reject các yêu cầu pending của nó. */
    private void softDeleteDuplicate(Vehicle oldDuplicate) {
        oldDuplicate.setActive(false);
        oldDuplicate.setOwnershipEndAt(LocalDateTime.now());
        requestRepository.findByVehicleIdAndStatus(
                        oldDuplicate.getId(), VehicleAccessRequestStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(VehicleAccessRequestStatus.REJECTED);
                    request.setReviewNote("Vehicle superseded by an approved duplicate-plate request");
                    request.setReviewedAt(LocalDateTime.now());
                });
        // saveAndFlush để UPDATE (is_active=0) commit ngay xuống DB, giải phóng
        // index unique biển số active trước khi chèn xe active mới.
        vehicleRepository.saveAndFlush(oldDuplicate);
    }

    private VehicleAccessRequestResponse toResponse(VehicleAccessRequest accessRequest) {
        List<VehicleAccessRequestDocument> documents =
                documentRepository.findByAccessRequestIdOrderBySortOrderAsc(accessRequest.getId());
        List<DocumentSummary> documentSummaries = documents.stream()
                .map(document -> new DocumentSummary(
                        String.valueOf(document.getId()),
                        document.getUrl(),
                        document.getMimeType(),
                        document.getOriginalName(),
                        document.getDocumentType()))
                .toList();
        return VehicleAccessRequestResponse.from(accessRequest, documentSummaries);
    }

    private void saveDocuments(
            VehicleAccessRequest accessRequest, List<MultipartFile> files, String documentType) {
        if (files == null || files.isEmpty()) {
            return;
        }
        int sortOrder = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String url = fileStorageService.store(file);
            VehicleAccessRequestDocument document = new VehicleAccessRequestDocument();
            document.setAccessRequest(accessRequest);
            document.setUrl(url);
            document.setOriginalName(file.getOriginalFilename());
            String mimeType = file.getContentType();
            document.setMimeType(mimeType == null ? "" : mimeType);
            document.setDocumentType(documentType);
            document.setSortOrder(sortOrder++);
            documentRepository.save(document);
        }
    }

    private void reviewBrandModelVerification(VehicleAccessRequest accessRequest, VehicleAccessRequestStatus status) {
        Vehicle vehicle = accessRequest.getVehicle();
        User reviewer = authService.currentUser();

        if (status == VehicleAccessRequestStatus.APPROVED) {
            boolean deferred = vehicle == null;
            if (deferred) {
                // Deferred draft: no vehicle existed yet, build one from the request.
                vehicle = new Vehicle();
                vehicle.setCustomer(accessRequest.getRequester());
                vehicle.setLicensePlate(accessRequest.getLicensePlate());
                vehicle.setCarType(
                        accessRequest.getCarType() == null || accessRequest.getCarType().isBlank()
                                ? "sedan"
                                : accessRequest.getCarType());
                vehicle.setManufactureYear(accessRequest.getManufactureYear());
                vehicle.setOwnershipStartAt(LocalDateTime.now());
            }
            // Nếu biển số đang bị một (những) xe ACTIVE khác giữ ⇒ KHÓA chúng (giữ
            // nguyên lịch sử) để chủ mới được gán biển. Flush KHÓA TRƯỚC khi chèn
            // xe active mới cùng biển để không vỡ filtered unique index.
            List<Vehicle> superseded = accessRequest.getLicensePlate() == null
                    ? List.of()
                    : vehicleRepository.findAllByLicensePlateAndIsActiveTrue(accessRequest.getLicensePlate());
            boolean hadLocked = !superseded.isEmpty();
            if (hadLocked) {
                LocalDateTime now = LocalDateTime.now();
                for (Vehicle v : superseded) {
                    v.setActive(false);
                    v.setOwnershipEndAt(now);
                }
                vehicleRepository.saveAllAndFlush(superseded);
            }
            VehicleBrand brand = resolveOrCreateBrand(accessRequest, vehicle);
            VehicleModel model = resolveOrCreateModel(accessRequest, vehicle, brand);
            if (brand != null) {
                vehicle.setBrandRef(brand);
                vehicle.setBrand(brand.getName());
            }
            if (model != null) {
                vehicle.setModelRef(model);
                vehicle.setModel(model.getName());
            }
            vehicle.setVerificationStatus(VehicleVerificationStatus.APPROVED);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setVerifiedBy(reviewer.getId());
            vehicle.setVerificationNote(accessRequest.getReviewNote());
            // Persist the new vehicle BEFORE linking it to the managed request, otherwise
            // the next query auto-flushes and fails on the transient reference.
            Vehicle saved = vehicleRepository.save(vehicle);
            if (hadLocked) {
                for (Vehicle v : superseded) {
                    v.setReplacedByVehicleId(saved.getId());
                }
                vehicleRepository.saveAll(superseded);
            }
            if (deferred) {
                accessRequest.setVehicle(saved);
            }
        } else {
            if (vehicle == null) {
                // Deferred draft rejected: nothing to mark, the vehicle simply doesn't exist.
                return;
            }
            vehicle.setVerificationStatus(VehicleVerificationStatus.REJECTED);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setVerifiedBy(reviewer.getId());
            vehicle.setVerificationNote(accessRequest.getReviewNote());
            vehicleRepository.save(vehicle);
        }
    }

    private VehicleBrand resolveOrCreateBrand(VehicleAccessRequest accessRequest, Vehicle vehicle) {
        if (accessRequest.getBrandRef() != null) {
            return accessRequest.getBrandRef();
        }
        String name = hasText(accessRequest.getSuggestedBrandName())
                ? accessRequest.getSuggestedBrandName().trim()
                : (hasText(accessRequest.getCatalogBrandName())
                    ? accessRequest.getCatalogBrandName().trim()
                    : (hasText(vehicle.getBrand()) ? vehicle.getBrand().trim() : null));
        if (name == null || isOther(name)) {
            return null;
        }
        return brandRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    VehicleBrand created = new VehicleBrand();
                    created.setName(name);
                    return brandRepository.save(created);
                });
    }

    private VehicleModel resolveOrCreateModel(VehicleAccessRequest accessRequest, Vehicle vehicle, VehicleBrand brand) {
        if (accessRequest.getModelRef() != null) {
            return accessRequest.getModelRef();
        }
        String name = hasText(accessRequest.getSuggestedModelName())
                ? accessRequest.getSuggestedModelName().trim()
                : (hasText(accessRequest.getCatalogModelName())
                    ? accessRequest.getCatalogModelName().trim()
                    : (hasText(vehicle.getModel()) ? vehicle.getModel().trim() : null));
        if (name == null || isOther(name)) {
            return null;
        }
        if (brand != null) {
            return modelRepository
                    .findByBrandIdAndNameIgnoreCase(brand.getId(), name)
                    .orElseGet(() -> {
                        VehicleModel created = new VehicleModel();
                        created.setBrand(brand);
                        created.setName(name);
                        created.setNew(true);
                        return modelRepository.save(created);
                    });
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isOther(String value) {
        return value != null && value.trim().equalsIgnoreCase("OTHER");
    }

    private VehicleAccessRequestStatus parseStatus(String status) {
        try {
            return VehicleAccessRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid vehicle access request status");
        }
    }

    public static class VehicleAccessRequestForm {
        private String licensePlate;
        private String relationship;
        private String note;
        private String suggestedBrandName;
        private String suggestedModelName;
        /** Hãng/dòng chọn từ catalog (luồng 2). */
        private String catalogBrandName;
        private String catalogModelName;
        private List<MultipartFile> documents;
        /** Minh chứng hãng/dòng xe (luồng xác minh cả hãng/dòng lẫn biển số). */
        private List<MultipartFile> brandModelDocuments;
        /** Minh chứng biển số (luồng xác minh cả hãng/dòng lẫn biển số). */
        private List<MultipartFile> plateDocuments;

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }

        public String getRelationship() {
            return relationship;
        }

        public void setRelationship(String relationship) {
            this.relationship = relationship;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getSuggestedBrandName() {
            return suggestedBrandName;
        }

        public void setSuggestedBrandName(String suggestedBrandName) {
            this.suggestedBrandName = suggestedBrandName;
        }

        public String getSuggestedModelName() {
            return suggestedModelName;
        }

        public void setSuggestedModelName(String suggestedModelName) {
            this.suggestedModelName = suggestedModelName;
        }

        public String getCatalogBrandName() {
            return catalogBrandName;
        }

        public void setCatalogBrandName(String catalogBrandName) {
            this.catalogBrandName = catalogBrandName;
        }

        public String getCatalogModelName() {
            return catalogModelName;
        }

        public void setCatalogModelName(String catalogModelName) {
            this.catalogModelName = catalogModelName;
        }

        public List<MultipartFile> getDocuments() {
            return documents;
        }

        public void setDocuments(List<MultipartFile> documents) {
            this.documents = documents;
        }

        public List<MultipartFile> getBrandModelDocuments() {
            return brandModelDocuments;
        }

        public void setBrandModelDocuments(List<MultipartFile> brandModelDocuments) {
            this.brandModelDocuments = brandModelDocuments;
        }

        public List<MultipartFile> getPlateDocuments() {
            return plateDocuments;
        }

        public void setPlateDocuments(List<MultipartFile> plateDocuments) {
            this.plateDocuments = plateDocuments;
        }
    }

    public record ReviewRequest(String reviewNote) {}

    public static class BrandModelResubmitForm {
        private Long vehicleId;
        private String licensePlate;
        private String note;
        private String suggestedBrandName;
        private String suggestedModelName;
        private String carType;
        private Integer manufactureYear;
        private List<MultipartFile> documents;

        public Long getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(Long vehicleId) {
            this.vehicleId = vehicleId;
        }

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getSuggestedBrandName() {
            return suggestedBrandName;
        }

        public void setSuggestedBrandName(String suggestedBrandName) {
            this.suggestedBrandName = suggestedBrandName;
        }

        public String getSuggestedModelName() {
            return suggestedModelName;
        }

        public void setSuggestedModelName(String suggestedModelName) {
            this.suggestedModelName = suggestedModelName;
        }

        public String getCarType() {
            return carType;
        }

        public void setCarType(String carType) {
            this.carType = carType;
        }

        public Integer getManufactureYear() {
            return manufactureYear;
        }

        public void setManufactureYear(Integer manufactureYear) {
            this.manufactureYear = manufactureYear;
        }

        public List<MultipartFile> getDocuments() {
            return documents;
        }

        public void setDocuments(List<MultipartFile> documents) {
            this.documents = documents;
        }
    }

    public record RequesterSummary(
            @JsonProperty("_id") String uid,
            String displayName,
            String phone) {}

    public record VehicleAccessRequestResponse(
            @JsonProperty("_id") String uid,
            String licensePlate,
            String relationship,
            String note,
            String status,
            String requestType,
            String reviewNote,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt,
            RequesterSummary requesterId,
            VehicleDtos.VehicleResponse vehicleId,
            List<DocumentSummary> documents,
            String suggestedBrandName,
            String suggestedModelName,
            String catalogBrandName,
            String catalogModelName,
            String carType,
            Integer manufactureYear,
            Long brandId,
            Long modelId) {
        public static VehicleAccessRequestResponse from(
                VehicleAccessRequest request, List<DocumentSummary> documents) {
            return new VehicleAccessRequestResponse(
                    String.valueOf(request.getId()),
                    request.getLicensePlate(),
                    request.getRelationship(),
                    request.getNote(),
                    request.getStatus().name().toLowerCase(),
                    request.getRequestType() == null
                            ? "access_request"
                            : request.getRequestType().name().toLowerCase(),
                    request.getReviewNote(),
                    request.getCreatedAt(),
                    request.getReviewedAt(),
                    new RequesterSummary(
                            String.valueOf(request.getRequester().getId()),
                            request.getRequester().getFullName(),
                            request.getRequester().getPhone()),
                    request.getVehicle() == null ? null : VehicleDtos.VehicleResponse.from(request.getVehicle()),
                    documents == null ? List.of() : documents,
                    request.getSuggestedBrandName(),
                    request.getSuggestedModelName(),
                    request.getCatalogBrandName(),
                    request.getCatalogModelName(),
                    request.getCarType(),
                    request.getManufactureYear(),
                    request.getBrandRef() == null ? null : request.getBrandRef().getId(),
                    request.getModelRef() == null ? null : request.getModelRef().getId());
        }
    }

    public record DocumentSummary(
            @JsonProperty("id") String id,
            String url,
            String mimeType,
            String name,
            String documentType) {}
}
