# Hotel Booking System

A high-performance REST API for hotel room booking management with built-in rate limiting, request queuing, and metrics tracking.

## Features

- Room booking management
- User booking history
- Rate limiting (833 requests/second)
- Request queuing for handling bursts
- Metrics tracking
- High-performance in-memory H2 database
- Connection pooling
- Caching layer for improved performance

## Technology Stack

- Java 11+
- H2 Database (In-memory)
- Jackson for JSON processing
- Maven for dependency management
- Custom HTTP server implementation
- Custom connection pooling
- Custom metrics registry

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Available port 8080 (default)

## Building the Application

```bash
mvn clean install
```

## Running the Application

```bash
java -jar target/hotel-booking-1.0.0.jar
```

## API Endpoints

### Create Booking

- **POST** `/api/bookings`
- Request Body:

```json
{
    "userId": 1,
    "roomId": 1,
    "checkInDate": "2025-09-01",
    "checkOutDate": "2025-09-05"
}
```

### Update Booking

- **PUT** `/api/bookings/{bookingId}`
- Request Body:

```json
{
    "checkInDate": "2025-09-02",
    "checkOutDate": "2025-09-06"
}
```

### Cancel Booking

- **DELETE** `/api/bookings/{bookingId}`

### Get Booking

- **GET** `/api/bookings/{bookingId}`

### Get User Bookings

- **GET** `/api/bookings/user/{userId}`

## Performance Characteristics

- Maximum concurrent requests: 833/second (3M/hour)
- Request queue size: 10,000 requests
- Request timeout: 2 seconds
- Queue timeout: 1 second
- Connection pool: Optimized for high concurrency

## Metrics

The application tracks various metrics including:

- Total HTTP requests
- Requests by HTTP method
- Success/Error rates
- Booking operations (create/update/cancel)
- User booking retrievals

## Error Handling

The API uses standard HTTP status codes:

- 200: Success
- 400: Bad Request
- 404: Not Found
- 429: Too Many Requests
- 500: Internal Server Error
- 503: Service Temporarily Unavailable

## Database Schema

### Rooms Table

```sql
CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    room_number VARCHAR(10) NOT NULL,
    room_type VARCHAR(50) NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL,
    max_occupancy INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### Bookings Table

```sql
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

## Testing

Run the tests using:

```bash
mvn test
```

The test suite includes:
- Unit tests for services
- Integration tests for HTTP handlers
- Load tests for concurrent request handling
- Cache effectiveness tests

## Local Development

1. Clone the repository

```bash
git clone https://github.com/vkpruthi/hotel-booking.git
cd hotel-booking
```

2. Build the project

```bash
mvn clean install
```

3. Run the application

```bash
java -jar target/hotel-booking-1.0-SNAPSHOT-jar-with-dependencies.jar
```

4. Test the API

```bash
# Create a booking
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"roomId":1,"checkInDate":"2025-09-01","checkOutDate":"2025-09-05"}'

# Get user bookings
curl http://localhost:8080/api/bookings/user/1
```

## Configuration

The application can be configured through environment variables:

- `SERVER_PORT`: HTTP server port (default: 8080)
- `DB_URL`: Database URL (default: jdbc:h2:mem:hoteldb)
- `DB_USER`: Database user (default: sa)
- `DB_PASSWORD`: Database password (default: empty)

## Monitoring

Metrics are available through the metrics endpoint:

- **GET** `/metrics`

Key metrics include:
- Request rates
- Error rates
- Response times
- Queue sizes
- Cache hit ratios

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please raise an issue in the GitHub repository.
