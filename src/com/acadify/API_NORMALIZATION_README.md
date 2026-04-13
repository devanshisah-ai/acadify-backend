# API Error Response Normalization - Implementation Guide

## Overview
This document explains the changes made to normalize all API responses across the Acadify backend system. All API endpoints now return a consistent, standardized JSON response format.

## Standard Response Format

### Success Response
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... }
}
```

### Error Response
```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

## Files Changed

### ✅ Core Utility Files (NEW/UPDATED)

#### 1. **ResponseUtil.java** (COMPLETELY REWRITTEN)
**Location:** `Backend/utils/ResponseUtil.java`

**Key Changes:**
- Added standardized response methods for all HTTP status codes
- All responses now follow the `{success, message, data}` format
- Methods provided:
  - `sendSuccess(exchange, message, data)` - 200 OK
  - `sendSuccess(exchange, message)` - 200 OK without data
  - `sendCreated(exchange, message, data)` - 201 Created
  - `sendBadRequest(exchange, message)` - 400 Bad Request
  - `sendUnauthorized(exchange, message)` - 401 Unauthorized
  - `sendForbidden(exchange, message)` - 403 Forbidden
  - `sendNotFound(exchange, message)` - 404 Not Found
  - `sendMethodNotAllowed(exchange)` - 405 Method Not Allowed
  - `sendConflict(exchange, message)` - 409 Conflict
  - `sendServerError(exchange, message)` - 500 Internal Server Error

**Deprecated Methods:**
- `sendError()` - Kept for backward compatibility, but use specific methods instead
- `sendJson()` - Kept for backward compatibility, automatically wraps non-standard responses

#### 2. **JsonBuilder.java** (NEW FILE)
**Location:** `Backend/utils/JsonBuilder.java`

**Purpose:** Helper class to construct JSON objects and arrays safely

**Usage Example:**
```java
String data = JsonBuilder.object()
    .add("user_id", userId)
    .add("role", role)
    .add("token", token)
    .build();

ResponseUtil.sendSuccess(exchange, "Login successful", data);
```

**Features:**
- Type-safe field addition (int, long, double, String, BigDecimal, Timestamp, boolean)
- Automatic JSON escaping
- Nullable field support with `addNullable()`
- Array building with `JsonBuilder.array()`
- Raw JSON support for nested objects

### ✅ Controller Files (UPDATED)

#### 3. **AuthController.java**
**Changes:**
- ❌ Removed: Raw `ResponseUtil.sendError()` calls
- ✅ Added: Specific response methods (`sendUnauthorized`, `sendBadRequest`, etc.)
- ✅ Added: JsonBuilder for constructing response data
- ✅ Improved: All responses now have meaningful success messages

**Before:**
```java
ResponseUtil.sendError(exchange, 401, "Invalid credentials");
```

**After:**
```java
ResponseUtil.sendUnauthorized(exchange, "Invalid email or password");
```

#### 4. **StudentController.java**
**Changes:**
- All endpoints now return standardized responses
- Replaced manual JSON string building with JsonBuilder
- Added meaningful success messages to all endpoints
- Proper use of response status codes (200, 201, 400, 404, 500)

**Endpoints Updated:**
- `/student/report` - Performance report
- `/student/semester-performance` - Semester data
- `/student/marks-trend` - Marks trend
- `/student/weak-subjects` - Weak subjects analysis
- `/student/doubt` - POST to raise doubts
- `/student/doubts` - GET all doubts
- `/student/activity` - Activity log

#### 5. **TeacherController.java**
**Changes:**
- All endpoints return normalized responses
- JsonBuilder used for all data construction
- Enhanced error messages with context
- Proper HTTP status code usage

**Endpoints Updated:**
- `/teacher/doubts` - Pending doubts
- `/teacher/doubt/answer` - POST to answer doubt
- `/teacher/marks` - POST/PUT marks management
- `/teacher/class-performance` - Class performance stats
- `/teacher/activity` - Activity log

#### 6. **AdminController.java**
**Changes:**
- Comprehensive normalization of all admin endpoints
- JsonBuilder for all response data
- Better error handling with specific messages
- Consistent success responses

**Endpoints Updated:**
- `/admin/create-student` - POST student creation
- `/admin/create-teacher` - POST teacher creation
- `/admin/create-subject` - POST subject creation
- `/admin/assign-teacher` - POST teacher assignment
- `/admin/top-performers` - GET top performers
- `/admin/lowest-performers` - GET lowest performers
- `/admin/backlogs` - GET students with backlogs
- `/admin/high-risk` - GET high-risk students
- `/admin/stream-performance` - GET stream statistics
- `/admin/active-term` - GET/POST active term
- `/admin/activity` - GET activity log

## Response Examples

### Example 1: Successful Login
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "user_id": 123,
    "role": "STUDENT",
    "token": "abc123xyz..."
  }
}
```

### Example 2: Validation Error
```json
{
  "success": false,
  "message": "Email and password are required",
  "data": null
}
```

### Example 3: Unauthorized Access
```json
{
  "success": false,
  "message": "Invalid email or password",
  "data": null
}
```

### Example 4: Resource Not Found
```json
{
  "success": false,
  "message": "Student profile not found",
  "data": null
}
```

### Example 5: Array Data Response
```json
{
  "success": true,
  "message": "Doubts retrieved successfully",
  "data": [
    {
      "doubt_id": 1,
      "teacher_id": 5,
      "question": "What is polymorphism?",
      "answer": null,
      "status": "PENDING",
      "created_at": "2025-02-04T10:30:00"
    },
    {
      "doubt_id": 2,
      "teacher_id": 5,
      "question": "Explain encapsulation",
      "answer": "Encapsulation is...",
      "status": "ANSWERED",
      "created_at": "2025-02-03T14:20:00"
    }
  ]
}
```

## Benefits of Normalization

### 1. **Frontend Consistency**
- Frontend can always check `success` field
- Frontend can always read `message` for user display
- Frontend can always access `data` for actual content
- No need to handle multiple response formats

### 2. **Error Handling**
```javascript
// Frontend can use one error handler
fetch('/api/student/report')
  .then(res => res.json())
  .then(response => {
    if (response.success) {
      // Handle success
      console.log(response.data);
    } else {
      // Show error to user
      alert(response.message);
    }
  });
```

### 3. **Security**
- No stack traces exposed in production
- No internal error details leaked
- Consistent, safe error messages
- Professional API behavior

### 4. **Maintainability**
- One place to change response format (ResponseUtil)
- Easy to add new endpoints following the same pattern
- Clear separation of concerns
- Type-safe JSON building

### 5. **API Documentation**
- Predictable response structure for documentation
- Easy to write API specs
- Better for API consumers (mobile apps, frontend)

## Migration Guide

### For New Endpoints
```java
// DON'T DO THIS
ResponseUtil.sendError(exchange, 400, "Bad request");

// DO THIS INSTEAD
ResponseUtil.sendBadRequest(exchange, "Email is required");
```

### For Existing Code
1. Replace `sendError()` with specific methods:
   - 400 → `sendBadRequest()`
   - 401 → `sendUnauthorized()`
   - 403 → `sendForbidden()`
   - 404 → `sendNotFound()`
   - 409 → `sendConflict()`
   - 500 → `sendServerError()`

2. Replace manual JSON with JsonBuilder:
```java
// OLD
String json = "{\"id\":" + id + ",\"name\":\"" + name + "\"}";
ResponseUtil.sendJson(exchange, 200, json);

// NEW
String data = JsonBuilder.object()
    .add("id", id)
    .add("name", name)
    .build();
ResponseUtil.sendSuccess(exchange, "Data retrieved", data);
```

## Testing Checklist

### ✅ Test Each Endpoint
1. **Success Case**: Verify `success: true` and proper data
2. **Validation Error**: Verify `success: false` and clear message
3. **Not Found**: Verify 404 with `success: false`
4. **Unauthorized**: Verify 401 with proper message
5. **Server Error**: Verify 500 doesn't leak internals

### ✅ Frontend Integration
1. Update frontend error handlers to check `success` field
2. Update success handlers to read from `data` field
3. Display `message` field to users appropriately

## Interview/Viva Explanation

**Q: What did you do for API normalization?**

**A:** "I implemented a standardized API response format across all endpoints. Every response now returns a consistent JSON structure with three fields: `success` (boolean), `message` (string), and `data` (object or null). This was achieved by creating a centralized ResponseUtil class with specific methods for each HTTP status code (sendSuccess, sendBadRequest, sendUnauthorized, etc.), ensuring that all endpoints follow the same pattern. Additionally, I created a JsonBuilder utility to safely construct JSON responses without manual string concatenation, which improves security and maintainability. This normalization simplifies frontend integration, enhances error handling, improves security by preventing information leakage, and makes the API more professional and predictable."

## Files Summary

### Created/Updated Files:
1. ✅ `ResponseUtil.java` - Centralized response handler (REWRITTEN)
2. ✅ `JsonBuilder.java` - JSON construction utility (NEW)
3. ✅ `AuthController.java` - Uses normalized responses
4. ✅ `StudentController.java` - Uses normalized responses
5. ✅ `TeacherController.java` - Uses normalized responses
6. ✅ `AdminController.java` - Uses normalized responses

### Unchanged Files:
- ❌ `DatabaseConfig.java` - No changes needed
- ❌ `EntityResolver.java` - No changes needed
- ❌ `InputValidator.java` - No changes needed
- ❌ `MainApplication.java` - No changes needed
- ❌ `PBKDF2Util.java` - No changes needed
- ❌ `RequestUtil.java` - No changes needed
- ❌ `SessionUtil.java` - No changes needed

## Conclusion

This normalization task improves the entire backend API by providing:
- ✅ Consistent response format across all endpoints
- ✅ Better error handling and user feedback
- ✅ Enhanced security (no information leakage)
- ✅ Simplified frontend integration
- ✅ Professional, production-grade API design
- ✅ Easier maintenance and extensibility

**STATUS: ✅ API ERROR RESPONSES NORMALIZED**
