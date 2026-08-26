-- V25: Cho phép nhiều xe mang cùng biển số MIỄN LÀ số đó đã bị bất hoạt (lịch sử),
-- chỉ ràng buộc unique trên các xe ACTIVE. Đồng thời bổ sung cột truyết (trace) để
-- biết xe cũ bị KHÓA (superseded) đã được xe mới nào thay thế — phục vụ yêu cầu #4:
-- "khóa xe cũ (không xóa), giữ nguyên lịch sử hoạt động sau khi xe được gán chủ mới".

-- 1) Bỏ unique index TOÀN CỤC hiện tại trên license_plate.
IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ux_vehicles_active_license_plate'
      AND object_id = OBJECT_ID(N'dbo.vehicles')
)
BEGIN
    DROP INDEX [ux_vehicles_active_license_plate] ON [dbo].[vehicles];
END;

-- 2) Tạo lại dưới dạng FILTERED index: chỉ một xe ACTIVE được giữ biển tại một thời điểm;
--    nhiều xe INACTIVE (lịch sử) vẫn có thể cùng giữ biển.
CREATE UNIQUE NONCLUSTERED INDEX [ux_vehicles_active_license_plate]
    ON [dbo].[vehicles] ([license_plate])
    WHERE [is_active] = 1;

-- 3) Cột trace: id của xe mới đã thay thế/xóa khóa xe cũ. NULL nếu xe không bị thay thế.
ALTER TABLE [dbo].[vehicles]
    ADD [replaced_by_vehicle_id] BIGINT NULL;