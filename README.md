# CodeAuth-Java-SDK
Official CodeAuth Java SDK

## Install
```xml
<dependency>
	<groupId>org.codeauth</groupId>
	<artifactId>codeauth-sdk</artifactId>
	<version>1.0.0</version>
</dependency>
```

## Sample Usage
```java
import org.codeauth.CodeAuthSDK;
CodeAuthSDK.Initialize("<your project API endpoint>", "<your project ID>", true, 5);
var result = CodeAuthSDK.SignInEmailVerify("<user email>", "<one time code>");
IO.println(result);
```
