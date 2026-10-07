import fs from "node:fs/promises";
import path from "node:path";

const API_URL = "https://api.github.com/graphql";
const CONFIG_PATH = process.env.SPONSORS_CONFIG ?? "sponsors.config.json";
const TOKEN = process.env.SPONSORS_TOKEN;

if (!TOKEN) {
  throw new Error(
    "SPONSORS_TOKEN is missing. Add it as a GitHub Actions repository secret."
  );
}

const config = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
validateConfig(config);

const START = "<!-- SPONSORS:START -->";
const END = "<!-- SPONSORS:END -->";

function validateConfig(value) {
  if (!value?.sponsorableLogin || value.sponsorableLogin === "CHANGE_ME") {
    throw new Error(
      "Set sponsorableLogin in sponsors.config.json to your sponsored GitHub login."
    );
  }

  if (!Array.isArray(value.tiers) || value.tiers.length === 0) {
    throw new Error("sponsors.config.json must define at least one tier.");
  }

  for (const tier of value.tiers) {
    if (!tier.name || !Number.isFinite(tier.min)) {
      throw new Error("Each tier needs a name and numeric min value.");
    }
  }
}

function getLevel(amount) {
  const tiers = [...config.tiers].sort((a, b) => b.min - a.min);
  return tiers.find((tier) => amount >= tier.min)?.name ?? null;
}

function getRank(level) {
  return config.tiers.find((tier) => tier.name === level)?.min ?? 0;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function graphql(query, variables) {
  const response = await fetch(API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
      "User-Agent": "meteo-sponsors-readme-updater"
    },
    body: JSON.stringify({ query, variables }),
    signal: AbortSignal.timeout(30_000)
  });

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(
      `GitHub GraphQL HTTP ${response.status}: ${JSON.stringify(body)}`
    );
  }

  if (body?.errors?.length) {
    throw new Error(
      `GitHub GraphQL error: ${body.errors.map((e) => e.message).join(" | ")}`
    );
  }

  return body.data;
}

const SPONSORS_QUERY = `
  query Sponsors($login: String!, $cursor: String) {
    repositoryOwner(login: $login) {
      ... on User {
        ...MaintainerSponsors
      }
      ... on Organization {
        ...MaintainerSponsors
      }
    }
  }

  fragment MaintainerSponsors on Sponsorable {
    sponsorshipsAsMaintainer(
      first: 100
      after: $cursor
      activeOnly: true
      includePrivate: false
    ) {
      nodes {
        isActive
        isOneTimePayment
        privacyLevel
        sponsorEntity {
          ... on User {
            login
            name
            avatarUrl(size: 144)
            url
          }
          ... on Organization {
            login
            name
            avatarUrl(size: 144)
            url
          }
        }
        tier {
          name
          monthlyPriceInDollars
          monthlyPriceInCents
          isCustomAmount
          isOneTime
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

async function fetchSponsors() {
  const all = [];
  let cursor = null;

  do {
    const data = await graphql(SPONSORS_QUERY, {
      login: config.sponsorableLogin,
      cursor
    });

    const sponsorable = data.repositoryOwner;
    if (!sponsorable) {
      throw new Error(
        `GitHub account '${config.sponsorableLogin}' was not found as a user or organization.`
      );
    }

    const connection = sponsorable.sponsorshipsAsMaintainer;
    all.push(...(connection.nodes ?? []));

    cursor = connection.pageInfo.hasNextPage
      ? connection.pageInfo.endCursor
      : null;
  } while (cursor);

  return all;
}

function normalizeSponsors(nodes) {
  const sponsors = [];

  for (const sponsorship of nodes) {
    // includePrivate:false already limits the API result, this is a second guard.
    if (sponsorship.privacyLevel !== "PUBLIC") continue;
    if (!sponsorship.isActive) continue;
    if (sponsorship.isOneTimePayment && !config.includeOneTime) continue;
    if (!sponsorship.sponsorEntity) continue;

    const amount = sponsorship.tier?.monthlyPriceInDollars;
    if (!Number.isFinite(amount)) {
      console.warn(
        `Skipping @${sponsorship.sponsorEntity.login}: tier amount is unavailable. ` +
        "Make sure SPONSORS_TOKEN belongs to the sponsored account or an authorized org owner."
      );
      continue;
    }

    const level = getLevel(amount);
    if (!level) {
      console.warn(
        `Skipping @${sponsorship.sponsorEntity.login}: $${amount} is below the lowest configured tier.`
      );
      continue;
    }

    sponsors.push({
      login: sponsorship.sponsorEntity.login,
      name: sponsorship.sponsorEntity.name ?? sponsorship.sponsorEntity.login,
      avatarUrl: sponsorship.sponsorEntity.avatarUrl,
      url: sponsorship.sponsorEntity.url,
      amount,
      level,
      oneTime: sponsorship.isOneTimePayment
    });
  }

  return sponsors.sort((a, b) => {
    const rank = getRank(b.level) - getRank(a.level);
    if (rank !== 0) return rank;
    return a.login.localeCompare(b.login, "en", { sensitivity: "base" });
  });
}

function buildReadmeHtml(sponsors) {
  if (sponsors.length === 0) {
    return `${START}\n<p>No public sponsors yet ❤️</p>\n${END}`;
  }

  const columns = Math.max(1, Number(config.columns) || 3);
  const avatarSize = Math.max(32, Number(config.avatarSize) || 72);
  const badgeWidth = Math.max(80, Number(config.badgeWidth) || 150);

  const cells = sponsors.map((sponsor) => {
    const login = escapeHtml(sponsor.login);
    const url = escapeHtml(sponsor.url);
    const avatar = escapeHtml(sponsor.avatarUrl);
    const badge = `assets/badges/${encodeURIComponent(sponsor.level)}.png`;
    const alt = escapeHtml(`${sponsor.level} sponsor`);

    return [
      '    <td align="center" valign="top">',
      `      <a href="${url}">`,
      `        <img src="${avatar}" width="${avatarSize}" height="${avatarSize}" alt="${login}" />`,
      `        <br /><strong>@${login}</strong>`,
      "      </a>",
      "      <br />",
      `      <img src="${badge}" width="${badgeWidth}" alt="${alt}" />`,
      "    </td>"
    ].join("\n");
  });

  const rows = [];
  for (let i = 0; i < cells.length; i += columns) {
    rows.push(`  <tr>\n${cells.slice(i, i + columns).join("\n")}\n  </tr>`);
  }

  return [
    START,
    "<!-- Generated automatically by scripts/update-sponsors.mjs. Do not edit inside this block. -->",
    "<table>",
    ...rows,
    "</table>",
    END
  ].join("\n");
}

async function updateReadme(sponsors) {
  const readmePath = config.readmePath ?? "README.md";
  const current = await fs.readFile(readmePath, "utf8");

  if (!current.includes(START) || !current.includes(END)) {
    throw new Error(
      `${readmePath} must contain both ${START} and ${END}. See README_SNIPPET.md.`
    );
  }

  const block = buildReadmeHtml(sponsors);
  const pattern = /<!-- SPONSORS:START -->[\s\S]*?<!-- SPONSORS:END -->/;
  const updated = current.replace(pattern, block);

  if (updated !== current) {
    await fs.writeFile(readmePath, updated, "utf8");
    console.log(`Updated ${readmePath}.`);
  } else {
    console.log(`${readmePath} already up to date.`);
  }
}

async function updatePerSponsorBadges(sponsors) {
  const outDir = "badges";
  await fs.rm(outDir, { recursive: true, force: true });
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, ".gitkeep"), "");

  for (const sponsor of sponsors) {
    const safeLogin = sponsor.login.toLowerCase();
    if (!/^[a-z0-9-]+$/.test(safeLogin)) {
      console.warn(`Skipping unsafe badge filename for @${sponsor.login}.`);
      continue;
    }

    const source = path.join("assets", "badges", `${sponsor.level}.png`);
    const destination = path.join(outDir, `${safeLogin}.png`);
    await fs.copyFile(source, destination);
  }

  console.log(`Generated ${sponsors.length} stable per-sponsor badge(s) in ${outDir}/.`);
}

const nodes = await fetchSponsors();
const sponsors = normalizeSponsors(nodes);

await updateReadme(sponsors);
await updatePerSponsorBadges(sponsors);

const summary = sponsors.reduce((acc, sponsor) => {
  acc[sponsor.level] = (acc[sponsor.level] ?? 0) + 1;
  return acc;
}, {});

console.log(`Public active sponsors rendered: ${sponsors.length}`);
console.log(summary);
