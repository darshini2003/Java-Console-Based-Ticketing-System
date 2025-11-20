# Service Request Management System

Simple Java console application (single-file) implementing a basic ticketing / service request system.

Run (from project root) on Windows PowerShell:

```powershell
# compile
javac -d out src/ServiceRequestSystem.java

# run
java -cp out src.ServiceRequestSystem
```

Notes:
- Requires Java 8 or higher.
- No external dependencies.
- The admin PIN is hard-coded to `1234` (demo only).
- The `data/` directory contains sample data files used by the app.

I removed compiled `.class` files from the repository and added this `.gitignore` to keep the repo clean.
