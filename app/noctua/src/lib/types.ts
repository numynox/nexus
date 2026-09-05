export interface Article {
  id: string;
  feed_id: string;
  feed_name: string;
  feed_icon: string | null;
  title: string;
  url: string;
  published: string | null;
  updated: string | null;
  author: string | null;
  summary: string | null;
  image_url: string | null;
  tags: string[];
}

export interface Feed {
  id: string;
  name: string;
  url: string;
  icon: string | null;
  sort_order: number;
}

export interface Section {
  id: string;
  name: string;
  /** A key from SECTION_ICONS, not an emoji — see lib/sectionMeta.ts */
  icon: string;
  color: string | null;
  sort_order: number;
  feeds: Feed[];
}

export interface UserContent {
  isLoggedIn: boolean;
  sections: Section[];
  articles: Article[];
  selectedSectionId: string | null;
}
