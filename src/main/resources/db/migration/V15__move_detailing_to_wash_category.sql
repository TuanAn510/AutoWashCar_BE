-- Move "Chăm Sóc Toàn Diện" (id=3) from "Car Care" to "Car Wash" category
-- so all 3 wash services are in the same category and mutually exclusive

UPDATE dbo.services
SET category_id = (
    SELECT id FROM dbo.service_categories WHERE name = 'Car Wash'
)
WHERE id = 3
  AND category_id = (
    SELECT id FROM dbo.service_categories WHERE name = 'Car Care'
  );
GO