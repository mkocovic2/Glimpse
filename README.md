# Glimpse
Glimpse is a minimalist system monitoring tool that gives you real-time insights into CPU, RAM, GPU, and processes — both locally and from remote servers. Designed with a sleek, hacker-style interface, Glimpse lets you visualize performance data at a glance.

✨ **Features**

- **Local System Monitoring**  
  Get detailed performance metrics directly from your local machine.

- **Remote System Monitoring**  
  Connect to and monitor other systems by providing their API URL and API key.

- **Real-time Data Visualization**  
  Interactive LineChart graphs display live CPU and GPU usage over time.

- **Detailed Resource Panels**  
  Dedicated sections for:
  - **Hardware**: Overview of CPU model, cores, threads, RAM, GPU, and disk information.
  - **Processes**: Dynamic list of running processes with CPU/memory usage, PIDs, threads, and users. Includes sorting.
  - **CPU**: Usage, frequency, process/thread counts, temperature.
  - **GPU**: Usage, memory, driver info, temperature (simulated for some metrics).
  - **RAM**: Usage and detailed memory stats.
  - **Network**: Live download/upload speeds, total data transferred, connection details.
  - **Disk**: Space usage, I/O speeds, partition info.

- **Intuitive User Interface**  
  Built with JavaFX, featuring a modern dark theme, circular indicators, and responsive layouts.

- **Remote Station Management**  
  Easily add and switch between multiple remote monitoring stations.

- **Modular Architecture**  
  Designed with a `BaseMonitor` class for easy extension and new module integration.

---
