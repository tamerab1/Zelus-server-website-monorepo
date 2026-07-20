# 

### Jrebel

in `userhome/.jrebel/jrebel.properties`

add

```
idea.outpath=\\out\\production\\classes
gradle.resources=\\build\\resources\\main
gradle.java.main=\\build\\classes\\java\\main
gradle.kotlin.main=\\build\\classes\\kotlin\\main

reason.api=C\:\\Users\\..\\kronos-api
reason.server=C\:\\Users\\..\\kronos-server
reason.common=C\:\\Users\\..\\common
```

- replace `..` with correct path for ur pc
- `\\` MUST be used as path seperators, not single `/\` or `/`

