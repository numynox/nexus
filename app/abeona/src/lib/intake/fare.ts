/**
 * A printed fare name → Abeona's fare type, and whether such a ticket is
 * bound to its trains. Mirrored in Kotlin (domain/intake/Fare.kt); the
 * cases are in intake.fixtures.json ("fares").
 */

/** abeona_trips.fare_type. */
export type FareTypeValue = "flex" | "saver" | "regional" | "deutschlandticket" | "bahncard100" | "other";

interface FareRule {
  pattern: RegExp;
  fare_type: FareTypeValue;
  /** Whether a ticket of this kind is bound to the booked trains. */
  binding: boolean;
}

// Order matters: the first match wins ("Sparpreis" before "Flexpreis"
// because "Flexpreis Plus" never says Sparpreis, while "Super Sparpreis"
// would also match a looser rule).
const RULES: FareRule[] = [
  { pattern: /deutschland-?ticket|\bd-ticket|jobticket deutschland/i, fare_type: "deutschlandticket", binding: false },
  { pattern: /bahncard\s*100/i, fare_type: "bahncard100", binding: false },
  // Trenitalia's Economy and Super Economy are its saver fares, bound to the train.
  { pattern: /sparpreis|super ?spar|sparschiene|saver|\beconomy\b/i, fare_type: "saver", binding: true },
  // Trenitalia's Base can be changed to another train, like a Flexpreis.
  { pattern: /flexpreis|flex ?ticket|normalpreis(?! nahverkehr)|fl[eé]x|^base$/i, fare_type: "flex", binding: false },
  {
    pattern:
      /quer-durchs-land|sch[öo]nes-wochenende|l[äa]nder-?ticket|bayern-?ticket|niedersachsen-?ticket|sachsen-?ticket|th[üu]ringen-?ticket|baden-w[üu]rttemberg-?ticket|hessenticket|nrw-?ticket|rheinland-pfalz-?ticket|saarland-?ticket|brandenburg-berlin-?ticket|mecklenburg-vorpommern-?ticket|schleswig-holstein-?ticket|nahverkehr|regio|verbund/i,
    fare_type: "regional",
    binding: false,
  },
];

/** The fare type and train binding a printed fare name implies; "other", unbound, when Abeona does not know it. */
export function fareOf(name: string | null): { fare_type: FareTypeValue; binding: boolean } {
  if (name) {
    for (const rule of RULES) {
      if (rule.pattern.test(name)) return { fare_type: rule.fare_type, binding: rule.binding };
    }
  }
  return { fare_type: "other", binding: false };
}

