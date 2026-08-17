package com.shinecraft.server.vehicle;

import com.shinecraft.server.common.BaseEntity;
import com.shinecraft.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(nullable = false, length = 20)
    private String licensePlate;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(length = 40)
    private String color;

    private Integer manufactureYear;

    @Column(nullable = false, length = 20)
    private String carType = "sedan";

    @Column(nullable = false)
    private boolean isActive = true;

    @OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<VehicleImage> images = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private VehicleBrand brandRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private VehicleModel modelRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleVerificationStatus verificationStatus = VehicleVerificationStatus.APPROVED;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String verificationNote;

    private LocalDateTime verifiedAt;

    private Long verifiedBy;

    @Column(nullable = false)
    private LocalDateTime ownershipStartAt = LocalDateTime.now();

    private LocalDateTime ownershipEndAt;

    /** Id của xe mới đã thay thế (thay thế vị trí giữ biển số) và gây khóa xe này.
     *  Khác null nghĩa là xe này bị bất hoạt do biển số được gán cho chủ mới,
     *  lịch sử hoạt động của xe vẫn được giữ lại. */
    private Long replacedByVehicleId;
}
