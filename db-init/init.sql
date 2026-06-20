-- Runs automatically on the FIRST start of the Postgres container
-- (only when the data volume is empty). Creates one database per microservice.
CREATE DATABASE guestdb;
CREATE DATABASE roomdb;
CREATE DATABASE reservationdb;
CREATE DATABASE paymentdb;
