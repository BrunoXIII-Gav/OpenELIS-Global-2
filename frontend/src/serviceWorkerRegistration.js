// Function to register the service worker
export function registerServiceWorker() {
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      const swUrl = `./service-worker.js`;

      navigator.serviceWorker
        .register(swUrl)
        .then((registration) => {
          console.log(
            "Service Worker registered with scope:",
            registration.scope,
          );
        })
        .catch((error) => {
          console.error("Service Worker registration failed:", error);
        });
    });
  }
}

// Function to unregister the service worker
export function unregisterServiceWorker() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker
      .getRegistrations()
      .then((registrations) =>
        Promise.all(
          registrations.map((registration) =>
            registration.unregister().then((unregistered) => {
              console.log("Service Worker unregistered:", unregistered);
              return unregistered;
            }),
          ),
        ),
      )
      .catch((error) => {
        console.error("Service Worker unregistration failed:", error);
      });
  }

  if ("caches" in window) {
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.map((key) => {
            if (key.includes("workbox") || key.includes("precache") || key.includes("runtime")) {
              return caches.delete(key);
            }

            return Promise.resolve(false);
          }),
        ),
      )
      .catch((error) => {
        console.error("Cache cleanup failed:", error);
      });
  }
}
