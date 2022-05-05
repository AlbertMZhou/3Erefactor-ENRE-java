# Dependency: UseVar

An entity uses a var in its scope, which could be a local var, a field or a parameter.

## Supported pattern

```yaml
name: UseVar
```

### Syntax:

```txt
UseVar:
    class {
        var;
        method {
          var
        }
    }
```

#### Examples:

* Method Uses Local Var

```java
//Hello.java
public class Hello {
    public void getter(int num) {
        int i = 0;
        if (num > 0) {
            i = i + num;
        } else {
            /* ... */
        }
    }
}
```

```yaml
name: Method Uses Local Var
entity:
    items:
        -   name: Hello
            category: Class
        -   name: getter
            qualifiedName: Hello.getter
            category: Method
        -   name: num
            qualifiedName: Hello.getter.num
            category: Variable
        -   name: i
            qualifiedName: Hello.getter.i
            category: Variable
relation:
    items:
        -   src: file0/getter
            dest: file0/i
            category: UseVar
            r:
                d: Use
                e: o/Bug
                s:
                u:
        -   src: file0/getter
            dest: file0/num
            category: UseVar
            r:
                d: Use
                e: .
                s: x
                u: .
```

* Method Uses Field (By This)

```java
//Hello.java
public class Hello {
    int i = 0;
    public void getter(int num) {
        if (num > 0) {
            this.i = this.i + num;
        } else {
            /* ... */
        }
    }
}
```

```yaml
name: Method Uses Field By This
entity:
    items:
        -   name: Hello
            category: Class
        -   name: getter
            qualifiedName: Hello.getter
            category: Method
        -   name: num
            qualifiedName: Hello.getter.num
            category: Variable
        -   name: i
            qualifiedName: Hello.i
            category: Variable
relation:
    r:
        d: Use
        e: .
        s: Use
        u: .
    items:
        -   src: file0/getter
            dest: file0/i
            category: UseVar
        -   src: file0/getter
            dest: file0/num
            category: UseVar
```

* Method Uses Field

```java
//Hello.java
public class Hello {
    int i = 0;
    public void getter(int num) {
        if (num > 0) {
            i = i + num;
        } else {
            /* ... */
        }
    }
}
```

```yaml
name: Method Uses Field
entity:
    items:
        -   name: Hello
            category: Class
        -   name: getter
            qualifiedName: Hello.getter
            category: Method
        -   name: num
            qualifiedName: Hello.getter.num
            category: Variable
        -   name: i
            qualifiedName: Hello.i
            category: Variable
relation:
    r:
        d: Use
        e: .
        s: Use
        u: .
    items:
        -   src: file0/getter
            dest: file0/num
            category: UseVar
        -   src: file0/getter
            dest: file0/i
            category: UseVar
```

* Method Uses Parameter

```java
//Hello.java
public class Hello {
    public String getter(int num) {
        if (num > 0) {
            return "positive";
        } else {
            return "negative";
        }
    }
}
```

```yaml
name: Method Uses Parameter
entity:
    items:
        -   name: Hello
            category: Class
        -   name: getter
            qualifiedName: Hello.getter
            category: Method
        -   name: num
            qualifiedName: Hello.getter.num
            category: Variable
relation:
    r:
        d: Use
        e: .
        s: x
        u: .
    items:
        -   src: file0/getter
            dest: file0/num
            category: UseVar
```