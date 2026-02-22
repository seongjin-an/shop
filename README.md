## SHOP
### docker
- mysql
- kafka
- redis

### shop-m
- user and authentication service
- user session managed by redis session store 

### shop-f
- frontend

### shop-order
- ...

### shop-product
- product & inventory service

---
### message flow
```text
Order Service
  publish → ORDER_CREATED

Inventory Service
  consume → ORDER_CREATED
  publish → INVENTORY_RESERVED / INVENTORY_FAILED

Order Service
  consume → INVENTORY_RESERVED / INVENTORY_FAILED
  publish → PAYMENT_REQUESTED

Payment Service
  consume → PAYMENT_REQUESTED
  publish → PAYMENT_COMPLETED / PAYMENT_FAILED

Order Service
  consume → PAYMENT_COMPLETED / PAYMENT_FAILED
```

### RUN
- run docker container first
- run shop-f, shop-m, ...

