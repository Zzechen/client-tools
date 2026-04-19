import { sdkEventUrl } from "./sdk-client.js";

interface PageChangedEvent {
  event: string;
  activityName: string;
  timestamp: string;
}

class EventMonitor {
  private lastEvent: PageChangedEvent | null = null;
  private retryDelayMs = 1000;
  private stopped = false;

  start(): void {
    this.connect();
  }

  stop(): void {
    this.stopped = true;
  }

  getLastEvent(): PageChangedEvent | null {
    return this.lastEvent;
  }

  private connect(): void {
    if (this.stopped) return;

    fetch(sdkEventUrl())
      .then(async (res) => {
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);
        this.retryDelayMs = 1000;
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() ?? "";
          for (const line of lines) {
            if (line.startsWith("data:")) {
              try {
                const data = JSON.parse(line.slice(5).trim());
                if (data.event === "page_changed") {
                  this.lastEvent = data as PageChangedEvent;
                }
              } catch {
                // ignore malformed line
              }
            }
          }
        }
      })
      .catch(() => {
        // 连接失败，退避后重连
      })
      .finally(() => {
        if (!this.stopped) {
          setTimeout(() => this.connect(), this.retryDelayMs);
          this.retryDelayMs = Math.min(this.retryDelayMs * 2, 30000);
        }
      });
  }
}

export const eventMonitor = new EventMonitor();
