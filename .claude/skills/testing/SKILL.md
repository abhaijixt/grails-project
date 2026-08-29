---
name: testing
description: Use when writing, running, fixing, or reviewing tests in grails-bookstore. Trigger on "add a test", "write a spec", "run the tests", "the build failed", "this spec is failing", "how do I mock X", or before opening any PR that changes grails-app/. Covers Spock unit specs with @TestFor/@Mock, the flush trap, the GrailsParameterMap trap, and the Book.lock stub.
---

# Testing grails-bookstore

Spock on Grails 2.5.6. 8 specs, 70 tests, all unit — there are no integration tests.
Read `.claude/context/build-and-test.md` for timings and what CI runs.

## Run

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"

grails --non-interactive test-app unit:                                  # all — 19s
grails --non-interactive test-app unit: com.learning.bookstore.BookSpec  # one — 13s
.claude/hooks/test.sh --dry-run <changed file>                           # which specs apply
```

Boot is ~12s of both numbers, so narrowing saves about six seconds. Usually just run everything.

Failures land in `target/test-reports/`. The XML is easier to read than the console output:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for f in glob.glob('target/test-reports/TEST-*.xml'):
    r = ET.parse(f).getroot()
    for tc in r.iter('testcase'):
        for bad in list(tc.findall('failure')) + list(tc.findall('error')):
            print('###', tc.get('name')); print((bad.text or '')[:600])
PY
```

## Three traps that account for most red specs here

**1 · Save without flush.** The unit-test datastore answers queries and unique-constraint
checks against *flushed* state only. This passes when it should fail:

```groovy
validAuthor().save(failOnError: true)              // wrong
!validAuthor().validate()                          // returns true — no uniqueness seen

validAuthor().save(flush: true, failOnError: true) // right
```

The same bug makes `findAllBy...` return an empty list from data you just created.

**2 · A plain Map is not `params`.** `BookService.list` calls `params.int('size')`, which only
exists on `GrailsParameterMap`. Passing `[size: "500"]` throws `MissingMethodException`. Mix in
the controller mixin and use the `params` it provides:

```groovy
@TestFor(BookService)
@Mock([Book, Category, Author, OrderItem, BookOrder, Customer])
@TestMixin(ControllerUnitTestMixin)
class BookServiceSpec extends Specification {
    void "list caps the page size at 100"() {
        given: params.size = "500"
        expect: service.list(params).size == 100
    }
}
```

The same mixin is what registers the JSON marshallers — without it `ApiResponseService`'s
converter cannot serialise a plain `Map`.

**3 · `lock()` does not exist in the mock datastore.** `OrderService.place` calls
`Book.lock(id)`. Stub it, and restore afterwards:

```groovy
def setup()   { Book.metaClass.static.lock = { Serializable id -> Book.get(id) } }
def cleanup() { Book.metaClass = null }
```

## Shared fixtures and unique constraints

`Category.name` is unique. A helper that builds a fresh `Category` per call fails on the
*category*, not the thing under test. Build one in `setup()` and reuse it — `BookSpec` does.

## Spock rules that bite

- `thrown()` must be a top-level statement in a `then:` block. You cannot write
  `(thrown(X) != null) == flag` — split the `@Unroll` into an "is allowed" and an "is refused"
  feature instead. `OrderServiceSpec` does exactly that for the status machine.
- `where:` blocks with an `|| outcome` column read well in failure output; use them for
  boundary tables (`BookSpec` price, `CustomerSpec` phone).

## What must be tested before a PR

- New service logic: the happy path **and** every `IllegalArgumentException` it throws.
- New domain constraint: the boundary on both sides — `BookSpec` tests `0.01` and `0.00`,
  `9999.99` and `10000.00`.
- New controller action: the status codes it can return.
- A widened `OrderStatus.canTransitionTo`: the spec first, then the enum.

CI runs `grails --non-interactive test-app unit:` and nothing else. If it is not a unit spec,
nothing checks it.
