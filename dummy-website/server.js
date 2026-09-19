const express = require('express');
const app = express();
const PORT = process.env.PORT || 3001;

const NAV = `
  <nav>
    <a href="/">Home</a>
    <a href="/about">About</a>
    <a href="/services">Services</a>
    <a href="/contact">Contact</a>
  </nav>
`;

const STYLE = `
  <style>
    * { box-sizing: border-box; }
    body { font-family: -apple-system, Helvetica, Arial, sans-serif; margin: 0; color: #1a1a2e; background: #fafafa; }
    header { background: #0c2340; color: white; padding: 1.25rem 3rem; display: flex; justify-content: space-between; align-items: center; }
    header .brand { font-weight: 700; font-size: 1.2rem; letter-spacing: 0.02em; }
    nav a { color: #cbd8e8; text-decoration: none; margin-left: 1.75rem; font-size: 0.95rem; }
    nav a:hover { color: white; }
    main { max-width: 780px; margin: 0 auto; padding: 4rem 2rem; }
    h1 { font-size: 2.1rem; margin-bottom: 0.5rem; }
    .tag { color: #5a6b85; font-size: 1.05rem; margin-bottom: 2.5rem; }
    p { line-height: 1.7; color: #334; }
    .card { background: white; border: 1px solid #e3e6eb; border-radius: 10px; padding: 1.5rem; margin: 1.25rem 0; }
    footer { text-align: center; color: #8a94a6; font-size: 0.85rem; padding: 2.5rem 0; }
  </style>
`;

function page(title, body) {
  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${title} — Meridian Trust Financial</title>${STYLE}</head>
<body>
  <header><div class="brand">MERIDIAN TRUST FINANCIAL</div>${NAV}</header>
  <main>${body}</main>
  <footer>© 2026 Meridian Trust Financial. All rights reserved.</footer>
</body></html>`;
}

app.get('/', (req, res) => {
  res.send(page('Home', `
    <h1>Modern banking for growing businesses</h1>
    <p class="tag">Accounts, payroll, and lending — built for finance teams who move fast.</p>
    <div class="card"><p><strong>Business Checking</strong> — no monthly fees, same-day transfers, built-in expense tracking.</p></div>
    <div class="card"><p><strong>Payroll &amp; Benefits</strong> — automated payroll runs, tax filing, and health benefits in one place.</p></div>
    <div class="card"><p><strong>Lines of Credit</strong> — flexible working capital, approved in as little as 24 hours.</p></div>
  `));
});

app.get('/about', (req, res) => {
  res.send(page('About', `
    <h1>About Meridian Trust</h1>
    <p class="tag">Founded in 2014, trusted by over 12,000 businesses.</p>
    <p>Meridian Trust Financial was founded with a simple idea: business banking shouldn't slow businesses down. Today we serve companies from five-person startups to established mid-market firms, with a platform built for speed, transparency, and control.</p>
    <p>Our team combines decades of banking experience with a modern engineering culture, so our customers get the reliability of a bank with the responsiveness of a product built this year — not fifteen years ago.</p>
  `));
});

app.get('/services', (req, res) => {
  res.send(page('Services', `
    <h1>Services</h1>
    <p class="tag">Everything a growing finance team needs, in one platform.</p>
    <div class="card"><p><strong>Business Banking</strong> — checking, savings, and treasury management.</p></div>
    <div class="card"><p><strong>Payroll Processing</strong> — full-service payroll with automated compliance.</p></div>
    <div class="card"><p><strong>Corporate Cards</strong> — physical and virtual cards with built-in spend controls.</p></div>
    <div class="card"><p><strong>API &amp; Integrations</strong> — connect Meridian to the tools your finance team already uses.</p></div>
  `));
});

app.get('/contact', (req, res) => {
  res.send(page('Contact', `
    <h1>Contact us</h1>
    <p class="tag">We typically respond within one business day.</p>
    <div class="card"><p><strong>Sales</strong> — sales@meridiantrust.example</p></div>
    <div class="card"><p><strong>Support</strong> — support@meridiantrust.example</p></div>
    <div class="card"><p><strong>Press</strong> — press@meridiantrust.example</p></div>
  `));
});

app.listen(PORT, () => {
  console.log(`dummy-website listening on http://localhost:${PORT}`);
});
