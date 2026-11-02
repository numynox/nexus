<script lang="ts">
  import emptyTrips from "../../illustrations/empty-trips.svg?raw";
  import station from "../../illustrations/station.svg?raw";
  import trainHero from "../../illustrations/train-hero.svg?raw";
  import yearReview from "../../illustrations/year-review.svg?raw";

  type Name = "empty-trips" | "station" | "train-hero" | "year-review";

  interface Props {
    name: Name;
    class?: string;
  }

  let { name, class: className = "" }: Props = $props();

  // Inlined rather than <img>, so the drawings take their colours from the
  // theme (the --il-* variables in global.css).
  const SOURCES: Record<Name, string> = {
    "empty-trips": emptyTrips,
    station,
    "train-hero": trainHero,
    "year-review": yearReview,
  };
</script>

<div class="illustration {className}" aria-hidden="true">
  {@html SOURCES[name]}
</div>
