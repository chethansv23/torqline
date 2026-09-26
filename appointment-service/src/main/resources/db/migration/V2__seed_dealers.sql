insert into dealer (id, name, city, timezone, open_time, close_time) values
    ('TQ-BLR-IND', 'Torqline Indiranagar', 'Bengaluru', 'Asia/Kolkata', '09:00', '18:00'),
    ('TQ-BLR-WHF', 'Torqline Whitefield',  'Bengaluru', 'Asia/Kolkata', '09:00', '19:00');

-- Indiranagar is car-heavy; Whitefield sees more two-wheelers.
insert into service_bay (id, dealer_id, name, vehicle_type) values
    (1,  'TQ-BLR-IND', 'Car Lift 1',   'CAR'),
    (2,  'TQ-BLR-IND', 'Car Lift 2',   'CAR'),
    (3,  'TQ-BLR-IND', 'Car Lift 3',   'CAR'),
    (4,  'TQ-BLR-IND', 'Bike Stand 1', 'BIKE'),
    (5,  'TQ-BLR-IND', 'Bike Stand 2', 'BIKE'),
    (6,  'TQ-BLR-WHF', 'Car Lift 1',   'CAR'),
    (7,  'TQ-BLR-WHF', 'Car Lift 2',   'CAR'),
    (8,  'TQ-BLR-WHF', 'Bike Stand 1', 'BIKE'),
    (9,  'TQ-BLR-WHF', 'Bike Stand 2', 'BIKE'),
    (10, 'TQ-BLR-WHF', 'Bike Stand 3', 'BIKE'),
    (11, 'TQ-BLR-WHF', 'Bike Stand 4', 'BIKE');
