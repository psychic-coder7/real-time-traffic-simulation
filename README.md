# Real-time Traffic Simulation (Java + SUMO)

A Java Swing application that controls a [SUMO](https://eclipse.dev/sumo/) traffic simulation in real time through TraCI (libtraci).
You can start and stop the simulation, inject vehicles on alternative routes, switch traffic lights manually or by a simple rule, and watch live traffic metrics on an interactive map.

University team project, Frankfurt University of Applied Sciences.

---

## Features

- **Live SUMO connection** via libtraci; the simulation loop runs in its own thread so the Swing UI never freezes
- **Interactive map** rendered from the SUMO network file (`.net.xml`): zoom, pan, rotate, reset (double-click or `R`)
- **Vehicle injection**: cars, trucks and buses on four alternative long routes (A–D) per scenario, computed with via-points and `Simulation.findRoute()`
- **Traffic light control**: force red / green, reset to the original program, or enable a rule-based stop/go mode driven by queue length
- **Live metrics**: active and visible vehicles, vehicles by type, average waiting time, congestion index, throughput (vehicles/hour over a 5-minute window), mean speed, plus a trend chart
- **Map filters** by vehicle type and minimum speed
- **Export**: metrics as CSV and a PDF summary including the trend chart
- **Logging** to console and rotating log files

## Architecture

| Class | Responsibility |
|---|---|
| `Main` | Startup checks (SUMO config present), loads route data, launches the GUI |
| `GUI` | Swing layout, controls and event wiring |
| `LiveConnectionSumo` | Simulation loop in a background thread, metric calculation, CSV/PDF export |
| `MapVisualisation` | Parses `.net.xml` (with XXE-safe XML parsing), world-to-screen transform, map and trend chart rendering |
| `TrafficControl` | Manual and rule-based traffic light control |
| `VehicleInjection` | Route parsing, route-variant generation and caching, version-tolerant vehicle injection |
| `Logging` | Logger setup (console + file) |

Thread safety: the UI thread and the simulation thread share state through `volatile` fields, `ConcurrentHashMap` and synchronized lists; label and chart updates are scheduled on the Swing event thread with `SwingUtilities.invokeLater`.

## Requirements

- **Java 17**
- **Maven 3**
- **SUMO** installed so that `sumo-gui` runs from the command line. Use a SUMO version close to the libtraci version in `pom.xml` (currently 1.24.0) to avoid native library mismatches.

## Project structure

```
pom.xml                     Maven build configuration
src/main/java/org/example/  Java source code
sumo/                       SUMO configuration (final.sumocfg, final.net.xml, final.rou.xml)
Project Documentation.pdf   Project documentation
```

## How to run

Run all commands from the project root (the folder containing `pom.xml`).

1. Check your installation:

   ```bash
   java -version        # should show 17.x
   mvn -v
   sumo-gui --version
   ```

2. Build (the first build downloads dependencies):

   ```bash
   mvn clean compile
   ```

3. Start the application:

   ```bash
   mvn exec:java -Dexec.mainClass="org.example.Main"
   ```

4. In the window, click **Start**. SUMO GUI opens with `sumo/final.sumocfg`. Then you can:
   - set the simulation speed (1x–10x)
   - choose a scenario and spawn **Car**, **Truck** or **Bus**
   - pick a traffic light and press **Red**, **Green** or **Reset**, or enable **Rule-based TLS (stop/go)**
   - filter the map, and use **Export CSV** / **Export PDF**

## Troubleshooting

- **`sumo-gui` not found**: add SUMO's `bin` folder to your `PATH` and set `SUMO_HOME`.
- **Native library / `UnsatisfiedLinkError`**: the SUMO installation and the libtraci version in `pom.xml` differ; align them.
- **"Missing SUMO config file" at startup**: start the app from the project root so `sumo/final.sumocfg` can be found.
