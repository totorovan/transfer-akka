REST API for money transfers between accounts.

## Overview
 - implemented in Java 8
 - using akka-actor to ensure thread safety (without need of blocking), responsiveness, resilience and elasticity
 - using akka-http as HTTP "framework" which is built on top of akka-actor
 - storing actor refs in-memory
 - possibility to deploy actors across different jvm instances
 - possibility to persist actors in NoSQL databases (e.g. Redis)

## REST API

##### Account
| Method | URI | Description |
| :---: | :---: | :---: |
| GET | /accounts/[id] | Retrieve account by id |
| POST | /accounts | Create account |
| DELETE | /accounts/[id] | Delete account |
 
 ##### Transaction
| Method | URI | Description |
| :---: | :---: | :---: |
| GET | /transactions/[id] | Retrieve transaction by id |
| POST | /transactions | Create transaction and do money transfer |
| DELETE | /transactions/[id] | Delete transaction|
 
 
## How to run
To build the project:
```
./gradlew build
```
To test:
```
./gradlew test
```
To run:
```
./gradlew run
```

### Notes
Please change `server.address` property in `application.properties` file to bootstrap the application on the different port if the default one is occupied.