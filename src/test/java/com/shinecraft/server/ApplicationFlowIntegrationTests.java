package com.shinecraft.server;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shinecraft.server.audit.AuditLog;
import com.shinecraft.server.audit.AuditLogRepository;
import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.catalog.CarWashService;
import com.shinecraft.server.catalog.CarWashServiceRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyAccountRepository;
import com.shinecraft.server.loyalty.LoyaltyService;
import com.shinecraft.server.loyalty.LoyaltyTransaction;
import com.shinecraft.server.loyalty.LoyaltyTransactionRepository;
import com.shinecraft.server.loyalty.LoyaltyTransactionType;
import com.shinecraft.server.loyalty.MembershipTier;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.loyalty.PointLot;
import com.shinecraft.server.loyalty.PointLotRepository;
import com.shinecraft.server.loyalty.Reward;
import com.shinecraft.server.loyalty.RewardRedemption;
import com.shinecraft.server.loyalty.RewardRedemptionRepository;
import com.shinecraft.server.loyalty.RewardRedemptionStatus;
import com.shinecraft.server.loyalty.RewardRepository;
import com.shinecraft.server.loyalty.RewardType;
import com.shinecraft.server.promotion.PromotionRepository;
import com.shinecraft.server.promotion.Promotion;
import com.shinecraft.server.promotion.DiscountType;
import com.shinecraft.server.promotion.PromotionService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationFlowIntegrationTests {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000000);
    private static final AtomicInteger SLOT_SEQUENCE = new AtomicInteger();
    private static final DateTimeFormatter JSON_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private CarWashServiceRepository serviceRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionService promotionService;

    @MockitoSpyBean
    private BookingRepository bookingRepository;

    @Autowired
    private LoyaltyService loyaltyService;

    @Autowired
    private LoyaltyAccountRepository loyaltyAccountRepository;

    @Autowired
    private LoyaltyTransactionRepository transactionRepository;

    @Autowired
    private PointLotRepository pointLotRepository;

    @Autowired
    private RewardRepository rewardRepository;

    @Autowired
    private RewardRedemptionRepository redemptionRepository;

    @Autowired
    private MembershipTierRepository tierRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void resetBookingRepositorySpy() {
        reset(bookingRepository);
    }

    @Test
    void authMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isForbidden());
    }

    @Test
    void registerLoginAndCurrentUserWork() throws Exception {
        CustomerContext customer = registerCustomer();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "password": "Password@123"
                                }
                                """
                                .formatted(customer.phone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.user.phone", is(customer.phone())));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.phone", is(customer.phone())))
                .andExpect(jsonPath("$.data.role", is("customer")))
                .andExpect(jsonPath("$.data.legacyRole", is("ROLE_CUSTOMER")));
    }

    @Test
    void refreshTokenRotatesAndOldTokenCannotBeReused() throws Exception {
        CustomerContext customer = registerCustomer();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """
                                .formatted(customer.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.phone", is(customer.phone())))
                .andReturn();

        String rotatedRefreshToken =
                JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.data.refreshToken");
        assertThat(rotatedRefreshToken).isNotEqualTo(customer.refreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """
                                .formatted(customer.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        CustomerContext customer = registerCustomer();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """
                                .formatted(customer.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """
                                .formatted(customer.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void registerAndLoginNormalizePhoneNumber() throws Exception {
        int unique = SEQUENCE.getAndIncrement();
        String localPhone = "09" + unique;
        String formattedPhone = "+84 " + localPhone.substring(1, 4) + "." + localPhone.substring(4);
        String plate = "IT-NORM-" + unique;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Normalized Customer",
                                  "phone": "%s",
                                  "password": "Password@123",
                                  "licensePlate": "%s",
                                  "brand": "Toyota",
                                  "model": "Vios",
                                  "color": "White",
                                  "manufactureYear": 2022
                                }
                                """
                                .formatted(formattedPhone, plate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phone", is(localPhone)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "password": "Password@123"
                                }
                                """
                                .formatted(formattedPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phone", is(localPhone)));
    }

    @Test
    void customerCanCreateBooking() throws Exception {
        CustomerContext customer = registerCustomer();
        CarWashService service = firstActiveService();
        LocalDateTime scheduledAt = nextSlot();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "serviceIds": [%d],
                                  "scheduledAt": "%s",
                                  "promotionId": null,
                                  "rewardRedemptionId": null,
                                  "note": "Integration test booking"
                                }
                                """
                                .formatted(customer.vehicle().getId(), service.getId(), scheduledAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.vehicleId", is(customer.vehicle().getId().intValue())))
                .andExpect(jsonPath("$.data.services[0].serviceId", is(service.getId().intValue())));
    }

    @Test
    void customerCanCreateAppointmentAtAnArbitraryMinute() throws Exception {
        CustomerContext customer = registerCustomer();
        LocalDateTime scheduledAt = nextSlot().withMinute(17);

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(customer, null, scheduledAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scheduledAt", is(scheduledAt.format(JSON_DATE_TIME))));
    }

    @Test
    void activeSlotCannotBeDoubleBooked() throws Exception {
        CustomerContext firstCustomer = registerCustomer();
        CustomerContext secondCustomer = registerCustomer();
        LocalDateTime scheduledAt = nextSlot();

        createBooking(firstCustomer, null, scheduledAt);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(secondCustomer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(secondCustomer, null, scheduledAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void cancelledSlotCanBeBookedAgain() throws Exception {
        CustomerContext firstCustomer = registerCustomer();
        CustomerContext secondCustomer = registerCustomer();
        String adminToken = loginAdmin();
        LocalDateTime scheduledAt = nextSlot();
        Long bookingId = createBooking(firstCustomer, null, scheduledAt);

        mockMvc.perform(patch("/api/admin/bookings/{id}/status", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CANCELLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(secondCustomer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(secondCustomer, null, scheduledAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scheduledAt", is(scheduledAt.format(JSON_DATE_TIME))))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void completingBookingAddsLoyaltyPoints() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Long bookingId = createBooking(customer, null);

        updateBookingStatus(adminToken, bookingId, "CONFIRMED");
        updateBookingStatus(adminToken, bookingId, "IN_QUEUE");
        updateBookingStatus(adminToken, bookingId, "IN_PROGRESS");

        mockMvc.perform(statusPatch(adminToken, bookingId, "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.earnedPoints", greaterThan(0)));

        mockMvc.perform(get("/api/loyalty/me").header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPoints", greaterThan(0)))
                .andExpect(jsonPath("$.data.lifetimePoints", greaterThan(0)))
                .andExpect(jsonPath("$.data.visitCount", is(1)));
    }

    @Test
    void bookingStatusCannotSkipWorkflow() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Long bookingId = createBooking(customer, null);

        mockMvc.perform(statusPatch(adminToken, bookingId, "COMPLETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void cancellationRestoresPromotionUsageOnceAndRepeatedCancellationDoesNotRestoreAgain() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Promotion promotion = createActivePromotion("P02-CANCEL-" + SEQUENCE.getAndIncrement());
        Long bookingId = createBooking(customer, promotion.getId());

        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(1);

        mockMvc.perform(statusPatch(adminToken, bookingId, "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isZero();

        mockMvc.perform(statusPatch(adminToken, bookingId, "CANCELLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void cancellationWithoutPromotionDoesNotChangePromotionUsage() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Promotion unrelatedPromotion = createActivePromotion("P02-NO-PROMO-" + SEQUENCE.getAndIncrement());
        Long bookingId = createBooking(customer, null);

        mockMvc.perform(statusPatch(adminToken, bookingId, "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        assertThat(promotionRepository.findById(unrelatedPromotion.getId()).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void bookingFailureAfterPromotionClaimRollsBackPromotionUsage() throws Exception {
        CustomerContext customer = registerCustomer();
        Promotion promotion = createActivePromotion("P02-ROLLBACK-" + SEQUENCE.getAndIncrement());
        doThrow(new DataIntegrityViolationException("forced booking persistence failure"))
                .when(bookingRepository)
                .save(any(Booking.class));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(customer, promotion.getId(), nextSlot())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)));

        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(promotionAuditLogsForPromotion(promotion.getId())).isEmpty();
    }

    @Test
    void successfulPromotionBookingCreatesOnePromotionUsedAudit() throws Exception {
        CustomerContext customer = registerCustomer();
        Promotion promotion = createActivePromotion("P04-USED-" + SEQUENCE.getAndIncrement());
        Long bookingId = createBooking(customer, promotion.getId());

        List<AuditLog> auditLogs = promotionAuditLogsForBooking(bookingId);
        assertThat(auditLogs).hasSize(1);
        AuditLog audit = auditLogs.get(0);
        assertThat(audit.getActor().getId()).isEqualTo(customer.user().getId());
        assertThat(audit.getTargetUser().getId()).isEqualTo(customer.user().getId());
        assertThat(audit.getAction()).isEqualTo("PROMOTION_USED");
        assertThat(audit.getBeforeValue()).isEqualTo(promotionAuditValue(promotion, bookingId, 0));
        assertThat(audit.getAfterValue()).isEqualTo(promotionAuditValue(promotion, bookingId, 1));
    }

    @Test
    void successfulCancellationCreatesOnePromotionRestoredAuditAndRepeatedCancellationCreatesNone() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Promotion promotion = createActivePromotion("P04-RESTORED-" + SEQUENCE.getAndIncrement());
        Long bookingId = createBooking(customer, promotion.getId());

        mockMvc.perform(statusPatch(adminToken, bookingId, "CANCELLED"))
                .andExpect(status().isOk());

        List<AuditLog> auditLogs = promotionAuditLogsForBooking(bookingId);
        assertThat(auditLogs).hasSize(2);
        AuditLog restoreAudit = auditLogs.stream()
                .filter(audit -> audit.getAction().equals("PROMOTION_RESTORED"))
                .findFirst()
                .orElseThrow();
        assertThat(restoreAudit.getActor().getId()).isEqualTo(customer.user().getId());
        assertThat(restoreAudit.getTargetUser().getId()).isEqualTo(customer.user().getId());
        assertThat(restoreAudit.getBeforeValue()).isEqualTo(promotionAuditValue(promotion, bookingId, 1));
        assertThat(restoreAudit.getAfterValue()).isEqualTo(promotionAuditValue(promotion, bookingId, 0));

        mockMvc.perform(statusPatch(adminToken, bookingId, "CANCELLED"))
                .andExpect(status().isBadRequest());
        assertThat(promotionAuditLogsForBooking(bookingId)).hasSize(2);
    }

    @Test
    void bookingWithoutPromotionCreatesNoPromotionAudit() throws Exception {
        CustomerContext customer = registerCustomer();
        Long bookingId = createBooking(customer, null);

        assertThat(promotionAuditLogsForBooking(bookingId)).isEmpty();
    }

    @Test
    void concurrentClaimsForSingleUsePromotionAllowExactlyOneSuccess() throws Exception {
        Promotion promotion = createActivePromotion("P03-LIMIT-ONE-" + SEQUENCE.getAndIncrement());
        promotion.setUsageLimit(1);
        promotionRepository.save(promotion);

        assertThat(concurrentClaimSuccesses(promotion.getId())).isEqualTo(1);
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void concurrentClaimsForFinalAvailableUsageAllowExactlyOneSuccess() throws Exception {
        Promotion promotion = createActivePromotion("P03-FINAL-USE-" + SEQUENCE.getAndIncrement());
        promotion.setUsageLimit(10);
        promotion.setUsedCount(9);
        promotionRepository.save(promotion);

        assertThat(concurrentClaimSuccesses(promotion.getId())).isEqualTo(1);
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(10);
    }

    @Test
    void concurrentClaimsForUnlimitedPromotionBothSucceed() throws Exception {
        Promotion promotion = createActivePromotion("P03-UNLIMITED-" + SEQUENCE.getAndIncrement());

        assertThat(concurrentClaimSuccesses(promotion.getId())).isEqualTo(2);
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(2);
    }

    @Test
    void bookingRollbackAfterPromotionClaimAllowsCompetingClaim() throws Exception {
        CustomerContext customer = registerCustomer();
        Promotion promotion = createActivePromotion("P03-ROLLBACK-" + SEQUENCE.getAndIncrement());
        promotion.setUsageLimit(1);
        promotionRepository.save(promotion);
        CountDownLatch bookingSaveReached = new CountDownLatch(1);
        CountDownLatch releaseBookingFailure = new CountDownLatch(1);
        CountDownLatch competingClaimStarted = new CountDownLatch(1);
        doThrowAfterLatch(bookingSaveReached, releaseBookingFailure);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> failedBooking = executor.submit(() -> {
                try {
                    mockMvc.perform(post("/api/bookings")
                                    .header("Authorization", bearer(customer.token()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bookingJson(customer, promotion.getId(), nextSlot())))
                            .andExpect(status().isInternalServerError());
                } catch (Exception exception) {
                    throw new AssertionError("Booking request should fail after promotion claim", exception);
                }
            });
            assertThat(bookingSaveReached.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> competingClaim = executor.submit(() -> {
                competingClaimStarted.countDown();
                promotionService.claimUsable(promotion.getId(), null);
                return true;
            });
            assertThat(competingClaimStarted.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBookingFailure.countDown();

            failedBooking.get(10, TimeUnit.SECONDS);
            assertThat(competingClaim.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseBookingFailure.countDown();
            executor.shutdownNow();
        }
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void expiredRewardRedemptionCannotBeAppliedToBooking() throws Exception {
        CustomerContext customer = registerCustomer();
        Reward reward = createReward("Expired voucher", RewardType.DISCOUNT_CODE, firstActiveService(), "1000");
        loyaltyService.earnPoints(customer.user(), BigDecimal.valueOf(100000), 10, "Test points", null);
        Long redemptionId = redeemReward(customer, reward);

        RewardRedemption redemption = redemptionRepository.findById(redemptionId).orElseThrow();
        redemption.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        redemptionRepository.save(redemption);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(customer, null, redemptionId, nextSlot())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        assertThat(redemptionRepository.findById(redemptionId).orElseThrow().getStatus())
                .isEqualTo(RewardRedemptionStatus.EXPIRED);
    }

    @Test
    void addOnRewardAddsFreeServiceToBooking() throws Exception {
        CustomerContext customer = registerCustomer();
        List<CarWashService> services = serviceRepository.findByIsActiveTrueOrderByNameAsc();
        CarWashService paidService = services.get(0);
        CarWashService addOnService = services.get(1);
        Reward reward = createReward("Free add-on", RewardType.ADD_ON, addOnService, null);
        loyaltyService.earnPoints(customer.user(), BigDecimal.valueOf(100000), 10, "Test points", null);
        Long redemptionId = redeemReward(customer, reward);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "serviceIds": [%d],
                                  "scheduledAt": "%s",
                                  "promotionId": null,
                                  "rewardRedemptionId": %d,
                                  "note": "Add-on reward booking"
                                }
                                """
                                .formatted(customer.vehicle().getId(), paidService.getId(), nextSlot(), redemptionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services[1].serviceId", is(addOnService.getId().intValue())))
                .andExpect(jsonPath("$.data.services[1].price", is(0)));
    }

    @Test
    void pointExpiryKeepsEarnTransactionHistory() {
        CustomerContext customer = registerCustomerUnchecked();
        loyaltyService.earnPoints(customer.user(), BigDecimal.valueOf(100000), 10, "Expirable points", null);
        PointLot lot = pointLotRepository.findAll().stream()
                .filter(pointLot -> pointLot.getCustomer().getId().equals(customer.user().getId()))
                .findFirst()
                .orElseThrow();
        lot.setExpiresAt(LocalDateTime.now().minusDays(1));
        pointLotRepository.save(lot);

        int expired = loyaltyService.expireOldPoints(LocalDateTime.now());

        LoyaltyAccount account = loyaltyAccountRepository.findByCustomer(customer.user()).orElseThrow();
        LoyaltyTransaction earn = transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getCustomer().getId().equals(customer.user().getId()))
                .filter(transaction -> transaction.getType() == LoyaltyTransactionType.EARN)
                .findFirst()
                .orElseThrow();
        assertThat(expired).isEqualTo(10);
        assertThat(account.getCurrentPoints()).isZero();
        assertThat(earn.getPoints()).isEqualTo(10);
        assertThat(pointLotRepository.findById(lot.getId()).orElseThrow().getRemainingPoints()).isZero();
    }

    @Test
    void monthlyReviewCanDowngradeTierByReviewWindow() {
        CustomerContext customer = registerCustomerUnchecked();
        MembershipTier lowestTier = tierRepository.findByIsActiveTrueOrderByMinPointsAsc().stream()
                .min(Comparator.comparing(MembershipTier::getMinPoints))
                .orElseThrow();
        MembershipTier highestTier = tierRepository.findByIsActiveTrueOrderByMinPointsAsc().stream()
                .max(Comparator.comparing(MembershipTier::getMinPoints))
                .orElseThrow();
        LoyaltyAccount account = loyaltyAccountRepository.findByCustomer(customer.user()).orElseThrow();
        account.setMembershipTier(highestTier);
        loyaltyAccountRepository.save(account);

        loyaltyService.monthlyReviewAndExpiry();

        LoyaltyAccount reviewed = loyaltyAccountRepository.findByCustomer(customer.user()).orElseThrow();
        assertThat(reviewed.getMembershipTier().getId()).isEqualTo(lowestTier.getId());
        assertThat(reviewed.getLastReviewedAt()).isNotNull();
    }

    @Test
    void adminCanManageUsersAndAuditChanges() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", customer.vehicle().getLicensePlate()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(customer.user().getId().intValue())));

        mockMvc.perform(patch("/api/admin/users/{id}/status", customer.user().getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active", is(false)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "password": "Password@123"
                                }
                                """
                                .formatted(customer.phone())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/users/{id}/status", customer.user().getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/{id}/reset-password", customer.user().getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "password": "NewPassword@123"
                                }
                                """
                                .formatted(customer.phone())))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void adminCanCreatePromotionAndCustomerCanUseIt() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        String code = "IT" + SEQUENCE.getAndIncrement();

        MvcResult promotionResult = mockMvc.perform(post("/api/admin/promotions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "title": "Integration promotion",
                                  "description": "Promotion created by integration test",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 10,
                                  "targetTierId": null,
                                  "startAt": "%s",
                                  "endAt": "%s",
                                  "usageLimit": 5,
                                  "active": true
                                }
                                """
                                .formatted(code, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is(code)))
                .andReturn();

        Integer promotionId = JsonPath.read(promotionResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/promotions/active").header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem(code)));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "serviceIds": [%d],
                                  "scheduledAt": "%s",
                                  "promotionId": %d,
                                  "rewardRedemptionId": null,
                                  "note": "Promotion booking"
                                }
                                """
                                .formatted(
                                        customer.vehicle().getId(),
                                        firstActiveService().getId(),
                                        nextSlot(),
                                        promotionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount", greaterThan(0.0)));

        assertThat(promotionRepository
                .findById(promotionId.longValue())
                .orElseThrow(() -> new AssertionError("Promotion should exist after creation"))
                .getUsedCount())
                .isEqualTo(1);
    }

    @Test
    void customerCanCreatePaymentAndAdminCanConfirmPaymentStatus() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Long bookingId = createBooking(customer, null);

        mockMvc.perform(post("/api/appointments/{id}/payment", bookingId)
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "vnpay"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.paymentUrl", notNullValue()))
                .andExpect(jsonPath("$.data.paymentId", notNullValue()))
                .andExpect(jsonPath("$.data.method", is("vnpay")))
                .andExpect(jsonPath("$.data.amount", notNullValue()));

        mockMvc.perform(patch("/api/appointments/{id}/payment-status", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "paid",
                                  "paymentMethod": "vnpay"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus", is("paid")))
                .andExpect(jsonPath("$.data.paymentMethod", is("vnpay")));
    }

    @Test
    void adminCanAssignStaffAndStaffCanSeeAssignedAppointment() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        String staffToken = loginStaff();
        User staff = firstActiveStaff();
        Long bookingId = createBooking(customer, null);

        mockMvc.perform(patch("/api/appointments/{id}/assign-staff", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d
                                }
                                """
                                .formatted(staff.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedStaffId._id", is(String.valueOf(staff.getId()))))
                .andExpect(jsonPath("$.data.assignedStaffId.role", is("staff")));

        mockMvc.perform(get("/api/appointments/staff/my")
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*]._id", hasItem(String.valueOf(bookingId))));
    }

    @Test
    void adminCanRescheduleAppointment() throws Exception {
        CustomerContext customer = registerCustomer();
        String adminToken = loginAdmin();
        Long bookingId = createBooking(customer, null);
        LocalDateTime newSlot = nextSlot();

        mockMvc.perform(patch("/api/appointments/{id}/reschedule", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledAt": "%s"
                                }
                                """
                                .formatted(newSlot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledAt", is(newSlot.format(JSON_DATE_TIME))));
    }

    private CustomerContext registerCustomer() throws Exception {
        int unique = SEQUENCE.getAndIncrement();
        String phone = "09" + unique;
        String plate = "IT" + unique;

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Integration Customer",
                                  "phone": "%s",
                                  "password": "Password@123",
                                  "licensePlate": "%s",
                                  "brand": "Toyota",
                                  "model": "Vios",
                                  "color": "White",
                                  "manufactureYear": 2022
                                }
                                """
                                .formatted(phone, plate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        String refreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
        User user = userRepository
                .findByPhone(phone)
                .orElseThrow(() -> new AssertionError("Registered customer should be persisted"));
        Vehicle vehicle = vehicleRepository
                .findByCustomerAndIsActiveTrue(user)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Registered customer should have a vehicle"));
        return new CustomerContext(phone, token, refreshToken, user, vehicle);
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "0900000000",
                                  "password": "Admin@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String loginStaff() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "0987654321",
                                  "password": "Staff@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long createBooking(CustomerContext customer, Long promotionId) throws Exception {
        return createBooking(customer, promotionId, nextSlot());
    }

    private Long createBooking(CustomerContext customer, Long promotionId, LocalDateTime scheduledAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(customer, promotionId, scheduledAt)))
                .andExpect(status().isOk())
                .andReturn();
        Integer bookingId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return bookingId.longValue();
    }

    private void updateBookingStatus(String adminToken, Long bookingId, String statusValue) throws Exception {
        mockMvc.perform(statusPatch(adminToken, bookingId, statusValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is(statusValue)));
    }

    private org.springframework.test.web.servlet.RequestBuilder statusPatch(
            String adminToken, Long bookingId, String statusValue) {
        return patch("/api/admin/bookings/{id}/status", bookingId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": "%s"
                        }
                        """
                        .formatted(statusValue));
    }

    private String bookingJson(CustomerContext customer, Long promotionId, LocalDateTime scheduledAt) {
        return bookingJson(customer, promotionId, null, scheduledAt);
    }

    private String bookingJson(CustomerContext customer, Long promotionId, Long redemptionId, LocalDateTime scheduledAt) {
        return """
                {
                  "vehicleId": %d,
                  "serviceIds": [%d],
                  "scheduledAt": "%s",
                  "promotionId": %s,
                  "rewardRedemptionId": %s,
                  "note": "Integration test booking"
                }
                """
                .formatted(
                        customer.vehicle().getId(),
                        firstActiveService().getId(),
                        scheduledAt,
                        promotionId == null ? "null" : promotionId.toString(),
                        redemptionId == null ? "null" : redemptionId.toString());
    }

    private CarWashService firstActiveService() {
        return serviceRepository
                .findByIsActiveTrueOrderByNameAsc()
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seed data should include at least one active service"));
    }

    private User firstActiveStaff() {
        return userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_STAFF)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seed data should include at least one active staff"));
    }

    private LocalDateTime nextSlot() {
        int index = SLOT_SEQUENCE.getAndIncrement();
        LocalDate date = LocalDate.now().plusDays(1 + (index / 4));
        LocalTime time = LocalTime.of(8, 0).plusMinutes((long) (index % 4) * 120);
        return date.atTime(time);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long redeemReward(CustomerContext customer, Reward reward) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rewards/{rewardId}/redeem", reward.getId())
                        .header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andReturn();
        Integer redemptionId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return redemptionId.longValue();
    }

    private Reward createReward(String name, RewardType type, CarWashService addOnService, String discountAmount) {
        Reward reward = new Reward();
        reward.setName(name + " " + SEQUENCE.getAndIncrement());
        reward.setRequiredPoints(1);
        reward.setRewardType(type);
        if (discountAmount != null) {
            reward.setDiscountAmount(new BigDecimal(discountAmount));
        }
        if (addOnService != null && type == RewardType.ADD_ON) {
            reward.setAddOnService(addOnService);
        }
        return rewardRepository.save(reward);
    }

    private Promotion createActivePromotion(String code) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setTitle(code);
        promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStartAt(LocalDateTime.now().minusMinutes(1));
        promotion.setEndAt(LocalDateTime.now().plusDays(1));
        return promotionRepository.save(promotion);
    }

    private List<AuditLog> promotionAuditLogsForBooking(Long bookingId) {
        return auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction().equals("PROMOTION_USED")
                        || audit.getAction().equals("PROMOTION_RESTORED"))
                .filter(audit -> audit.getAfterValue().contains("\"bookingId\":" + bookingId + ","))
                .toList();
    }

    private List<AuditLog> promotionAuditLogsForPromotion(Long promotionId) {
        return auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction().equals("PROMOTION_USED")
                        || audit.getAction().equals("PROMOTION_RESTORED"))
                .filter(audit -> audit.getAfterValue().contains("\"promotionId\":" + promotionId + ","))
                .toList();
    }

    private String promotionAuditValue(Promotion promotion, Long bookingId, int usedCount) {
        return "{\"promotionId\":%d,\"promotionCode\":\"%s\",\"bookingId\":%d,\"usedCount\":%d}"
                .formatted(promotion.getId(), promotion.getCode(), bookingId, usedCount);
    }

    private int concurrentClaimSuccesses(Long promotionId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstClaim = executor.submit(() -> claimAfterConcurrentStart(promotionId, ready, start));
            Future<Boolean> secondClaim = executor.submit(() -> claimAfterConcurrentStart(promotionId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return (firstClaim.get(10, TimeUnit.SECONDS) ? 1 : 0) + (secondClaim.get(10, TimeUnit.SECONDS) ? 1 : 0);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean claimAfterConcurrentStart(Long promotionId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            promotionService.claimUsable(promotionId, null);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private void doThrowAfterLatch(CountDownLatch bookingSaveReached, CountDownLatch releaseBookingFailure) {
        doAnswer(invocation -> {
                    bookingSaveReached.countDown();
                    if (!releaseBookingFailure.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to force the booking persistence failure");
                    }
                    throw new DataIntegrityViolationException("forced booking persistence failure");
                })
                .when(bookingRepository)
                .save(any(Booking.class));
    }

    private CustomerContext registerCustomerUnchecked() {
        try {
            return registerCustomer();
        } catch (Exception exception) {
            throw new AssertionError("Customer registration should succeed", exception);
        }
    }

    private record CustomerContext(String phone, String token, String refreshToken, User user, Vehicle vehicle) {}
}
