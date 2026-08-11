# Project Logic

This document describes the main business logic implemented in the Wash Car Service backend.

## 1. User And Role Logic

The system supports two primary roles:

- `ROLE_CUSTOMER`
- `ROLE_ADMIN`

Customer registration requires:

- full name
- phone number
- password
- first vehicle information
- license plate

Business rules:

- Phone number must be unique.
- License plate must be unique.
- A newly registered customer automatically receives a loyalty account.
- Login returns a JWT token.
- Secured APIs require `Authorization: Bearer <token>`.

## 2. Vehicle Logic

Customers can manage their own vehicles.

Business rules:

- A vehicle belongs to exactly one customer.
- Customers can only create and view their own vehicles.
- License plate is globally unique.

## 3. Catalog Logic

The catalog contains:

- service categories
- car wash services

Business rules:

- Public users can view active categories and services.
- Admin can create and update service categories.
- Admin can create and update services.
- A service must belong to a valid category.
- Service price and duration must be valid non-negative values.

Default seeded catalog:

- `Car Wash`
- `Car Care`
- `Basic Wash`
- `Premium Wash`
- `Interior Cleaning`

## 4. Booking Availability Logic

Booking slots are generated from:

- opening time: `08:00`
- closing time: `17:00`
- interval: `30 minutes`

The availability API returns all slots for a selected date and marks unavailable slots with a reason.

Possible unavailable reasons:

- `PAST`: the slot is earlier than the current time.
- `OUT_OF_TIER_WINDOW`: the slot is beyond the customer's membership booking window.
- `BOOKED`: the slot is already occupied by another active booking.

Occupied booking statuses:

- `PENDING`
- `CONFIRMED`
- `IN_QUEUE`
- `IN_PROGRESS`

Cancelled and completed bookings do not block a slot.

## 5. Booking Creation Logic

When a customer creates a booking, the backend performs these checks:

1. Verify the selected vehicle belongs to the current customer.
2. Load or create the customer's loyalty account.
3. Validate the booking date against the customer's tier booking window.
4. Validate the selected slot is between `08:00` and `17:00`.
5. Validate the selected slot is aligned to a 30-minute interval.
6. Reject the booking if the slot is already occupied.
7. Validate that all selected services exist and are active.
8. Calculate subtotal from selected service prices.
9. Apply membership tier discount if available.
10. Apply promotion discount if a promotion is selected.
11. Apply reward redemption discount if a redemption is selected.
12. Cap total discount so it never exceeds subtotal.
13. Save the booking with status `PENDING`.
14. Save selected services as booking snapshots.

Booking service snapshots store:

- original service id
- service name
- price
- duration

This keeps historical booking data stable even if admin later edits service prices or names.

## 6. Discount Logic

Discounts are applied in this order:

1. Membership tier discount.
2. Promotion discount.
3. Reward redemption discount.

Promotion discount types:

- `PERCENTAGE`: calculates a percentage discount from the current remaining amount.
- `FIXED_AMOUNT`: subtracts a fixed amount but cannot exceed the remaining amount.

Reward discount behavior:

- `FREE_WASH`: discounts the full remaining amount.
- `DISCOUNT_CODE`: subtracts the reward discount amount.
- `ADD_ON`: creates a reward option but does not directly reduce the booking amount in the current checkout logic.

Final amount formula:

```text
finalAmount = max(subtotal - discountAmount, 0)
```

## 7. Booking Status Logic

Admin can update booking status through the admin booking status API.

Supported booking statuses:

- `PENDING`
- `CONFIRMED`
- `IN_QUEUE`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

Current implementation note:

- The backend accepts any valid booking status enum from admin.
- It does not currently enforce a strict step-by-step transition flow.

When a booking is changed to `COMPLETED` for the first time:

1. `completedAt` is set.
2. Earned points are calculated from the final amount.
3. Points are added to the customer's loyalty account.
4. Customer spending and visit count are updated.
5. Customer membership tier is recalculated.
6. A loyalty transaction of type `EARN` is created.

Default earning rule:

```text
10,000 VND = 1 point
```

This value is configurable through:

```text
LOYALTY_POINTS_AMOUNT_UNIT
```

## 8. Loyalty Tier Logic

Each customer has one loyalty account.

The loyalty account tracks:

- current points
- lifetime points
- total spending
- visit count
- current membership tier
- last monthly review time

Tier selection rule:

```text
The active tier with the highest minPoints less than or equal to customer lifetimePoints is selected.
```

Default seeded tiers:

| Tier | Minimum Points | Discount | Booking Window | Priority |
|---|---:|---:|---:|---:|
| Member | 0 | 0% | 7 days | 0 |
| Silver | 500 | 5% | 10 days | 1 |
| Gold | 1500 | 10% | 12 days | 2 |
| Platinum | 3000 | 15% | 14 days | 3 |

Tier affects:

- booking window
- automatic membership discount
- priority queue order
- promotion eligibility

## 9. Point Expiry Logic

Earned points expire after a configured number of months.

Default:

```text
12 months
```

Configuration:

```text
LOYALTY_POINT_EXPIRY_MONTHS
```

Monthly scheduler:

```text
0 0 2 1 * *
```

The scheduler runs at 02:00 on the first day of each month and performs:

1. Expire old earned points.
2. Create `EXPIRE` loyalty transactions.
3. Recalculate active customer tiers.
4. Update `lastReviewedAt`.

## 10. Reward Logic

Customers can view active rewards and redeem a reward if they have enough current points.

When a reward is redeemed:

1. The backend checks the reward exists and is active.
2. The backend checks the customer has enough current points.
3. Required points are subtracted from current points.
4. A reward redemption code is generated.
5. A reward redemption record is created with status `AVAILABLE`.
6. A loyalty transaction of type `REDEEM` is created.

Reward redemption codes use this format:

```text
RW-XXXXXXXX
```

Reward redemptions expire after the same configured point expiry month value.

## 11. Promotion Logic

Promotions can be created and updated by admin.

A promotion can define:

- code
- title
- description
- discount type
- discount value
- target membership tier
- start date
- end date
- usage limit
- active flag

Business rules:

- End date must be after start date.
- Customer only sees active promotions within the active date range.
- Customer only sees promotions for their current tier, unless the promotion has no target tier.
- Promotion usage cannot exceed the configured usage limit.
- Promotion code is normalized to uppercase.

When a promotion is used in booking:

1. The backend verifies the promotion is active.
2. The backend verifies the current date is inside the promotion range.
3. The backend verifies the usage limit is not exceeded.
4. The backend verifies tier eligibility.
5. `usedCount` is increased by one.

## 12. Priority Queue Logic

Admin can view a priority booking queue.

Included statuses:

- `CONFIRMED`
- `IN_QUEUE`
- `IN_PROGRESS`

Sorting rules:

1. Higher membership tier priority first.
2. Earlier scheduled time first.

This means Platinum customers appear before Gold, Silver, and Member customers when their bookings are in the active service queue.

## 13. Survey Log Logic

The survey log feature supports temporary hosting and simulated user testing.

Public event logging supports events such as:

- `PAGE_VIEW`
- `CLICK`
- `FORM_START`
- `FORM_SUBMIT`
- `REGISTER`
- `LOGIN`
- `BOOKING_CREATED`

Stored data can include:

- session key
- user id if authenticated
- booking id if attached
- page
- action
- IP address
- user agent
- metadata JSON
- created time

Admin can view the latest survey logs for review and reporting.

## 14. Reporting Logic

Admin reporting includes:

- dashboard overview
- booking CSV export

Dashboard metrics include:

- customer count
- booking count
- completed booking count
- total revenue
- active promotions
- active rewards
- earned points
- redeemed points

The project also exposes:

- `/project-report.html`
- `/api/project-report`

These endpoints are designed for classroom/demo presentation.

## 15. Security Logic

Security is based on JWT and Spring Security.

Public endpoints include:

- health check
- register
- login
- catalog browsing
- survey log creation
- project report page/API
- Swagger/OpenAPI pages

Customer endpoints require authentication.

Admin endpoints require `ROLE_ADMIN`.

## 16. Data Seeding Logic

On application startup, the backend seeds default data if the target data does not already exist:

- default admin account
- membership tiers
- service categories
- services
- rewards

This allows the backend to run immediately after database migration without manual setup.
