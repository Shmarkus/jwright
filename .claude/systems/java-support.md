# jwright-java - Java Language Support

> **Role**: Java-specific extractors and writers
> **Module**: `jwright-java`
> **Package**: `ee.jwright.java.*`
> **Stability**: EXTENSION - Independent lifecycle
> **Critical Path**: Yes for Java projects

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Depends | jwright-core | `ContextExtractor`, `CodeWriter` | Implements contracts |
| ← Receives | jwright-engine | Via Spring discovery | Auto-registration |
| → Uses | JavaParser library | Internal | AST parsing |

## Quick Health
```bash
# Build Java support module
mvn compile -pl jwright-java

# Run Java-specific tests
mvn test -pl jwright-java
```

## Key Components
- `JavaTestMethodExtractor`: Extracts test method body and structure
- `JavaAssertionExtractor`: Parses JUnit/AssertJ assertions
- `JavaMockitoExtractor`: Extracts Mockito mock setups and verifies
- `JavaHintExtractor`: Reads `@JwrightHint` annotations
- `JavaTypeDefinitionExtractor`: Extracts relevant type info
- `JavaMethodBodyWriter`: Injects/replaces method implementations

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Package Structure

```
ee.jwright.java/
├── extract/
│   ├── JavaTestMethodExtractor.java      # Order: 100
│   ├── JavaAssertionExtractor.java       # Order: 200
│   ├── JavaMockitoExtractor.java         # Order: 300
│   ├── JavaHintExtractor.java            # Order: 400
│   ├── JavaTypeDefinitionExtractor.java  # Order: 600
│   └── JavaMethodSignatureExtractor.java # Order: 700
├── write/
│   └── JavaMethodBodyWriter.java         # Order: 100
└── JavaParserUtils.java                  # Shared parsing utilities
```

### Extractors

All extractors use JavaParser for AST analysis.

#### JavaTestMethodExtractor (Order: 100)
```java
@Component
@Order(100)
public class JavaTestMethodExtractor implements ContextExtractor {

    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".java");
    }

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        // Extracts: testClassName, testMethodName, testMethodBody
    }
}
```

#### JavaAssertionExtractor (Order: 200)
Parses assertion statements from test methods:
- JUnit 5: `assertEquals()`, `assertTrue()`, `assertThrows()`
- AssertJ: `assertThat().isEqualTo()`, `assertThat().contains()`
- Hamcrest: `assertThat()` with matchers

Populates `ExtractionContext.assertions`.

#### JavaMockitoExtractor (Order: 300)
Extracts Mockito constructs:
- `when(...).thenReturn(...)` → `MockSetup`
- `verify(...)` → `VerifyStatement`
- `@Mock` field declarations

Populates `ExtractionContext.mockSetups` and `verifyStatements`.

#### JavaHintExtractor (Order: 400)
Reads developer hints from annotations:
```java
@Test
@JwrightHint("Use StringBuilder for efficiency")
void shouldConcatenateStrings() { }
```

Populates `ExtractionContext.hints`.

#### JavaTypeDefinitionExtractor (Order: 600)
Extracts type information needed for implementation:
- Parameter types
- Return types
- Custom domain types referenced in test

Populates `ExtractionContext.typeDefinitions`.

#### JavaMethodSignatureExtractor (Order: 700)
Finds available methods on collaborators:
- Methods on mock objects
- Methods on injected dependencies

Populates `ExtractionContext.availableMethods`.

### Writer

#### JavaMethodBodyWriter (Order: 100)
```java
@Component
@Order(100)
public class JavaMethodBodyWriter implements CodeWriter {

    @Override
    public boolean supports(WriteRequest request) {
        return request.targetFile().toString().endsWith(".java");
    }

    @Override
    public WriteResult write(WriteRequest request) {
        // Uses JavaParser to:
        // - Parse target file
        // - Find target method
        // - Replace/inject body based on WriteMode
        // - Write formatted output
    }
}
```

**Write Modes:**
| Mode | Behavior |
|------|----------|
| `INJECT` | Insert body into existing empty method |
| `REPLACE` | Replace entire method body |
| `APPEND` | Add new method to class |
| `CREATE` | Create new file with class |

### Utility Class

```java
public class JavaParserUtils {
    public static CompilationUnit parse(Path file);
    public static Optional<MethodDeclaration> findMethod(CompilationUnit cu, String name);
    public static String extractMethodBody(MethodDeclaration method);
    public static void replaceMethodBody(MethodDeclaration method, String newBody);
}
```

## Adding Support for Other Languages

Follow this pattern to add Kotlin, Groovy, etc:

```java
// jwright-kotlin/src/.../KotlinTestMethodExtractor.java
@Component
@Order(100)
public class KotlinTestMethodExtractor implements ContextExtractor {

    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".kt");
    }

    // Use Kotlin compiler API for parsing
}
```

---

**Last Updated:** 2025-01-14
**Status:** Design complete
