## SHOP
### docker
- mysql
- kafka
- redis

### shop-user
- user and authentication service
- user session managed by redis session store 

### shop-frontend
- frontend

### shop-order
- ...

### shop-product
- product & stock service

---
### message flow
#### order
```text
Order Service
  publish → ORDER_CREATED

Stock Service
  consume → ORDER_CREATED
  publish → STOCK_RESERVED / STOCK_FAILED

Order Service
  consume → STOCK_RESERVED / STOCK_FAILED
  publish → PAYMENT_REQUESTED

Payment Service
  consume → PAYMENT_REQUESTED
  publish → PAYMENT_COMPLETED / PAYMENT_FAILED

Order Service
  consume → PAYMENT_COMPLETED / PAYMENT_FAILED
```
#### product
````text
Product Service
  publish -> PRODUCT_CREATED

Stock Service
  consume -> PRODUCT_CREATED
````

### SETUP & RUN
1. run docker container first

2. kafka topics
```bash
$ docker exec -it kafka kafka-topics --create --topic product-created --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

$ docker exec -it kafka kafka-topics --create --topic order-created --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

3. run shop-frontend, shop-user, ...

