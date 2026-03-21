# DentalClinic Management System

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Framework-brightgreen)
![Thymeleaf](https://img.shields.io/badge/Frontend-Thymeleaf-blue)
![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-red)
![Security](https://img.shields.io/badge/Auth-Spring%20Security-success)
![Architecture](https://img.shields.io/badge/Architecture-MVC-informational)

## 🦷 Overview

DentalClinic Management System is a web-based clinic management application built to support dental clinic operations, including appointment booking, examination management, medical records, payments, notifications, support tickets, and role-based access control.
The system is designed for multiple user roles such as **Admin**, **Staff**, **Dentist**, and **Customer**, with secure authentication and business-rule validation across both frontend and backend.

## ✨ Main Features

### 👤 Customer

* View available appointment time slots
* Create appointment and pay deposit
* Book multiple services in one appointment flow
* View booking history and appointment details
* Cancel appointment
* Receive notifications and appointment confirmation
* Rebook from appointment history
* View deposit receipt and payment history
* View medical records and prescriptions
* Rate dentist and service
* Send messages to receptionist
* Create and view medical support tickets
* Use wallet features such as top-up, refund, and transaction history
* Apply vouchers during booking/payment flow

### 👨‍⚕️ Dentist

* View work schedule
* Open examination page for assigned appointments
* Update examination information
* Create and update medical records
* Propose services during examination
* Transfer billing note after examination
* Respond to medical support tickets

### 🧑‍💼 Staff / Receptionist

* Manage appointments
* Support customers through messaging
* Coordinate booking and clinic operations
* Handle customer service processes

### 🛠️ Admin

* Manage users and roles
* Manage services
* Manage vouchers
* Monitor appointment/payment-related flows
* Configure and maintain system operations

## 💻 Technology Stack

* **Backend:** Java, Spring Boot, Spring MVC
* **Frontend:** Thymeleaf, HTML, CSS, JavaScript
* **Database:** SQL Server
* **ORM:** JPA / Hibernate
* **Security:** Spring Security
* **Build Tool:** Maven

## 🏗️ Architecture

The project follows the **MVC layered architecture**:

* **Controller**: handles HTTP requests and returns views/responses
* **Service**: contains business logic
* **Repository**: communicates with database
* **Model/Entity**: represents business domain and persistence objects
* **View (Thymeleaf)**: renders UI pages for users
  mermaid
  graph TD
  A[Customer / Dentist / Staff / Admin] --> B[Thymeleaf UI]
  B --> C[Spring MVC Controller]
  C --> D[Service Layer]
  D --> E[Repository Layer]
  E --> F[(SQL Server Database)]
  D --> G[Notification Module]
  D --> H[Payment / Wallet Module]
  D --> I[Medical Record Module]

```
mermaid
flowchart LR
    UI[Frontend - Thymeleaf] --> Controller[Controller]
    Controller --> Service[Service]
    Service --> Repository[Repository]
    Repository --> DB[(SQL Server)]
```

mermaid
graph LR
Customer --> Booking[Appointment Booking]
Customer --> Wallet[Wallet / Payment]
Customer --> Support[Support Ticket / Messaging]
Dentist --> Exam[Examination / Medical Record]
Staff --> Ops[Appointment Coordination]
Admin --> Mgmt[User / Service / Voucher Management]

````

## 📌 Core Business Rules
- Appointment booking is validated both on frontend and backend
- The system prevents booking in the past
- The system prevents booking in invalid time ranges such as lunch break
- Appointment slots are managed in 30-minute units
- If a service duration exceeds one slot, the following slot(s) are also reserved accordingly
- The system prevents overlapping bookings based on dentist/time-slot availability rules
- Deposits may be required to confirm appointment booking
- Refunds can be returned to wallet when cancellation conditions are met

## 📁 Project Structure
```text
src/
 ├── main/
 │   ├── java/com/dentalclinic/
 │   │   ├── controller/
 │   │   ├── service/
 │   │   ├── repository/
 │   │   ├── model/
 │   │   ├── dto/
 │   │   ├── config/
 │   │   └── exception/
 │   └── resources/
 │       ├── templates/
 │       ├── static/
 │       ├── application.properties
 │       └── ...
 └── test/
````

## ✅ Prerequisites

Before running the project, make sure you have installed:

* Java 17 or compatible version used by the project
* Maven 3.8+
* SQL Server
* Git

## ⚙️ Setup Instructions

### 1️⃣ Clone the repository

```bash
git clone <your-repository-url>
cd <your-project-folder>
```

### 2️⃣ Configure database

Update `src/main/resources/application.properties` with your local SQL Server configuration.

Example:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=DentalClinicDB;encrypt=true;trustServerCertificate=true
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### 3️⃣ Configure email (optional, if appointment confirmation email is enabled)

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.default-encoding=UTF-8
```

### 4️⃣ Build the project

```bash
mvn clean install
```

### 5️⃣ Run the application

```bash
mvn spring-boot:run
```

Or run the main Spring Boot application class directly from your IDE.

## 🔐 Default Access / Roles

The system supports role-based access control, including:

* `ADMIN`
* `STAFF`
* `DENTIST`
* `CUSTOMER`

If your project seeds default accounts, document them here:

```text
Admin: admin@example.com / ********
Staff: staff@example.com / ********
Dentist: dentist@example.com / ********
Customer: customer@example.com / ********
```

> Replace these placeholders with actual demo accounts if available.

## 📅 Appointment Flow Summary

1. Customer selects service(s)
2. Customer chooses available date/time slot
3. System validates business rules
4. Customer confirms booking information
5. Customer pays deposit if required
6. Appointment is created successfully
7. Notification / email confirmation can be sent after successful booking
   mermaid
   flowchart TD
   A[Select Services] --> B[Choose Date & Time Slot]
   B --> C[Validate Booking Rules]
   C --> D{Valid?}
   D -- No --> E[Show Validation Error]
   D -- Yes --> F[Confirm Booking]
   F --> G{Deposit Required?}
   G -- Yes --> H[Pay Deposit]
   G -- No --> I[Create Appointment]
   H --> I[Create Appointment]
   I --> J[Send Notification / Email Confirmation]

```

## 🩺 Examination Flow Summary
1. Dentist opens assigned appointment from work schedule
2. System verifies ownership of the appointment
3. Dentist enters examination information
4. Medical record is saved
5. Billing note can be prepared/transferred after examination
mermaid
flowchart TD
    A[Dentist opens appointment] --> B[Verify assigned dentist]
    B --> C[Open examination page]
    C --> D[Update examination result]
    D --> E[Save medical record]
    E --> F[Prepare billing note]
```

## 💳 Payment and Wallet Flow Summary

* Deposit payment for appointment booking
* Wallet top-up by card
* Refund to wallet on eligible cancellation
* View wallet transaction history
* View payment history and receipts
  mermaid
  flowchart LR
  TopUp[Top-up Wallet] --> Wallet[(Wallet Balance)]
  Wallet --> Deposit[Pay Deposit]
  Wallet --> ServicePay[Pay for Services]
  Cancel[Cancel Appointment] --> Refund[Refund to Wallet]
  Refund --> Wallet
  Wallet --> History[View Transaction History]

````

## 🧪 Testing
Run tests with:
```bash
mvn test
````

If the project currently has limited automated tests, manual testing should cover at least:

* login/logout
* appointment booking
* slot validation
* multi-service booking logic
* examination flow
* medical record flow
* billing/payment flow
* cancellation and refund
* support tickets and notifications

## 🚀 Future Improvements

* AI booking assistant
* Zalo confirmation / omnichannel notifications
* stronger audit logging
* retry mechanism for failed notifications
* reporting dashboard
* containerized deployment

## 🤝 Contribution Guidelines

1. Create a feature branch
2. Commit with clear messages
3. Open a pull request
4. Test affected flows before merging
5. Avoid breaking existing business logic

## 📝 Notes

* Keep source code naming in English for consistency
* UI text can be localized depending on the target users
* Be careful with UTF-8 encoding when displaying Vietnamese text
* Do not remove business-rule validations during refactoring

## 📄 License

This project is for educational / internal development purposes unless otherwise specified.
