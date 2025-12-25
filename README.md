# CodeAuth Java SDK
![GitHub License](https://img.shields.io/github/license/codeauth2/CodeAuth-Java-SDK)

Offical CodeAuth SDK. For more info, check the docs on our [official website](https://docs.codeauth.com).

## Installation
```xml
<dependency>
	<groupId>org.codeauth</groupId>
	<artifactId>codeauth-sdk</artifactId>
	<version>1.1.0</version>
</dependency>
```

## Basic Usage

### Initialize CodeAuth SDK
```java
import CodeAuthSDK.CodeAuth;
CodeAuth.Initialize("<your project API endpoint>", "<your project ID>")
```

### Signin / Email
Begins the sign in or register flow by sending the user a one time code via email.
```java
var result = CodeAuth.SignInEmail("<user email>");
switch (result.error)
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_email": IO.println("bad_email"); break;
	case "code_request_interval_reached": IO.println("code_request_interval_reached"); break;
	case "code_hourly_limit_reached": IO.println("code_hourly_limit_reached"); break;
	case "email_provider_error": IO.println("email_provider_error"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server
}
```

### Signin / Email Verify
Checks if the one time code matches in order to create a session token.
```java
var result = CodeAuth.SignInEmailVerify("<user email>", "<one time code>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_email": IO.println("bad_email"); break;
	case "bad_code": IO.println("bad_code"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server
}
IO.println(result.session_token);
IO.println(result.email);
IO.println(result.expiration);
IO.println(result.refresh_left);
```

### Signin / Social
Begins the sign in or register flow by allowing users to sign in through a social OAuth2 link.
```java
var result = CodeAuth.SignInSocial("<social_type>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_social_type": IO.println("bad_social_type"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server
}
IO.println(result.signin_url);
```

### Signin / Social Verify
This is the next step after the user signs in with their social account. This request checks the authorization code given by the social media company in order to create a session token.
```java
var result = CodeAuth.SignInSocialVerify("<social type>", "<code>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_social_type": IO.println("bad_social_type"); break;
	case "bad_code": IO.println("bad_authorization_code"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server
}
IO.println(result.session_token);
IO.println(result.email);
IO.println(result.expiration);
IO.println(result.refresh_left);
```

### Session / Info
Gets the information associated with a session token.
```java
var result = CodeAuth.SessionInfo("<session_token>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_session_token": IO.println("bad_session_token"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server
}
IO.println(result.email);
IO.println(result.expiration);
IO.println(result.refresh_left);
```

### Session / Refresh
Create a new session token using existing session token.
```java
var result = CodeAuth.SessionRefresh("<session_token>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_session_token": IO.println("bad_session_token"); break;
	case "out_of_refresh": IO.println("out_of_refresh"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break;//sdk failed to connect to api server
}
IO.println(result.session_token);
IO.println(result.email);
IO.println(result.expiration);
IO.println(result.refresh_left);
```

### Session / Invalidate
Invalidate a session token. By doing so, the session token can no longer be used for any api call.
```java
var result = CodeAuth.SessionInvalidate("<session_token>", "<invalidate_type>");
switch (result.error)
{
	case "bad_json": IO.println("bad_json"); break;
	case "project_not_found": IO.println("project_not_found"); break;
	case "bad_ip_address": IO.println("bad_ip_address"); break;
	case "rate_limit_reached": IO.println("rate_limit_reached"); break;
	case "bad_session_token": IO.println("bad_session_token"); break;
	case "bad_invalidate_type": IO.println("bad_invalidate_type"); break;
	case "internal_error": IO.println("internal_error"); break;
	case "connection_error": IO.println("connection_error"); break; //sdk failed to connect to api server 
}
```
