const puppeteer = require('puppeteer');

// Define network profiles
const PROFILE_SPIKE = [
  { speed: 1200, duration: 10 }, // Speed in Kbps, duration in seconds
  { speed: 300, duration: 10 },
  { speed: 800, duration: 10 },
];

const PROFILE_SLOW_JITTERS = [
  { speed: 500, duration: 5 },
  { speed: 1200, duration: 5 },
  { speed: 500, duration: 5 },
  { speed: 1200, duration: 5 },
  { speed: 500, duration: 5 },
  { speed: 1200, duration: 5 },
];

// Function to calculate throughput in bytes per second
const kbpsToBytesPerSecond = (kbps) => (kbps * 1024) / 8;

(async () => {
  const browser = await puppeteer.launch({
    headless: false, // Set to false to see the browser
    defaultViewport: null, // Full-screen viewport
  });

  const page = await browser.newPage();

  // Connect to Chrome DevTools Protocol
  const client = await page.target().createCDPSession();

  // Navigate to your application URL
  await page.goto('http://localhost:8000/index.html', { waitUntil: 'load', timeout: 60000 });

  console.log("Video playback started. Applying network profiles...");

  // Helper function to apply a network profile
  const applyProfile = async (profile, profileName) => {
    console.log(`\nStarting profile: ${profileName}`);
    for (const condition of profile) {
      console.log(
        `Setting speed to ${condition.speed} Kbps for ${condition.duration} seconds...`
      );

      // Apply network condition
      await client.send('Network.emulateNetworkConditions', {
        offline: false,
        latency: 50, // Assume a fixed latency for simplicity
        downloadThroughput: kbpsToBytesPerSecond(condition.speed),
        uploadThroughput: kbpsToBytesPerSecond(condition.speed / 2), // Upload speed is half the download
      });

      // Wait for the specified duration
      await new Promise(resolve => setTimeout(resolve, condition.duration * 1000));
    }
    console.log(`Finished profile: ${profileName}`);
  };

  // Apply profiles sequentially
  await applyProfile(PROFILE_SPIKE, 'PROFILE_SPIKE');
  await applyProfile(PROFILE_SLOW_JITTERS, 'PROFILE_SLOW_JITTERS');

  console.log("\nAll profiles tested. Closing browser...");
  await browser.close();
})();
