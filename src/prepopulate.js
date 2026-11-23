import { db } from './db';

// Default category tree for ColorNest — Dakota's curated taxonomy for full
// coloring book PDFs (the ColorNest/ folder tree in Google Drive). The
// "Coloring Pages Sorted" mirror for extracted single pages uses the same
// tree and is a Drive-side structure; the app mirrors its categories into
// the ColorNest root only.
const categoryTree = [
  { name: "Animals & Creatures", children: [
    { name: "Amphibians" },
    { name: "Avians & Birds", children: [
      { name: "Anatidae (Ducks, Geese & Swans)" },
      { name: "Corvidae (Crows, Ravens & Jays)" },
      { name: "Songbirds & Parrots" },
      { name: "Strigidae (Owls)" },
    ]},
    { name: "Canines & Dogs", children: [
      { name: "Canidae (Dogs, Wolves & Foxes)" },
      { name: "Domestic Dogs & Puppies" },
    ]},
    { name: "Cute Critters & Anthropomorphic Animals", children: [
      { name: "Farm & Barnyard Critters" },
      { name: "Pajama & Cozy Critters" },
    ]},
    { name: "Felines & Cats", children: [
      { name: "Big Cats" },
      { name: "Felidae (Cats)" },
      { name: "Kittens & Tiny Cats" },
    ]},
    { name: "Hoofed & Woodland Mammals", children: [
      { name: "Cervidae (Deer & Elk)" },
      { name: "Equidae (Horses, Zebras & Donkeys)" },
      { name: "Leporidae (Rabbits & Hares)" },
      { name: "Sciuridae (Squirrels & Chipmunks)" },
      { name: "Ursidae (Bears)" },
      { name: "Woodland & Wildlife" },
    ]},
    { name: "Insects, Arachnids & Pollinators", children: [
      { name: "Apidae (Bees & Bumblebees)" },
      { name: "Beetles, Dragonflies & Spiders" },
      { name: "Nymphalidae (Brush-footed Butterflies)" },
    ]},
    { name: "Marine & Aquatic Life", children: [
      { name: "Cheloniidae (Sea Turtles)" },
      { name: "Crustaceans & Mollusks" },
      { name: "Delphinidae (Dolphins & Orcas)" },
      { name: "Fish & Reef Life" },
      { name: "Whales, Rays & Jellies" },
    ]},
    { name: "Marsupials & Exotics", children: [
      { name: "Elephantidae (Elephants)" },
      { name: "Marsupials (Kangaroos, Koalas & Opossums)" },
      { name: "Tropical & Safari Wildlife" },
    ]},
    { name: "Prehistoric & Extinct Animals", children: [
      { name: "Dinosaurs" },
      { name: "Ice Age & Marine Prehistoric" },
    ]},
    { name: "Reptiles", children: [
      { name: "Lizards, Snakes & Tortoises" },
    ]},
  ]},
  { name: "Architecture, Rooms & Interiors", children: [
    { name: "Bedrooms, Studios & Personal Spaces", children: [
      { name: "Art Studios, Desks & Craft Rooms" },
      { name: "Cozy Bedrooms & Girl Spaces" },
    ]},
    { name: "Cafes, Bakeries & Shops", children: [
      { name: "Bakeries & Market Stalls" },
      { name: "Bookshops & Cafes" },
    ]},
    { name: "Cottages, Cabins & Houses", children: [
      { name: "Cottages & Country Homes" },
      { name: "Treehouses & Miniature Rooms" },
    ]},
  ]},
  { name: "Botanical & Nature", children: [
    { name: "Flowers & Bouquets", children: [
      { name: "Bouquets, Vases & Florals" },
      { name: "Succulents & Cacti" },
      { name: "Wildflowers & Meadows" },
    ]},
    { name: "Landscapes & Scenery", children: [
      { name: "Coastal, Beaches & Tidepools" },
      { name: "Gardens, Farms & Countryside" },
      { name: "Mountains, Valleys & Rivers" },
    ]},
    { name: "Mushrooms & Woodland Floor", children: [
      { name: "Fungi & Toadstools" },
      { name: "Moss, Ferns & Forest Floor" },
    ]},
    { name: "Sky, Weather & Celestial Nature", children: [
      { name: "Clouds, Sunsets & Rain" },
      { name: "Moon, Stars & Night Skies" },
    ]},
    { name: "Trees & Forests", children: [
      { name: "Ancient & Enchanted Trees" },
      { name: "Pine, Birch & Orchard Groves" },
    ]},
  ]},
  { name: "Cozy Scenes & Everyday Life", children: [
    { name: "Hygge, Slow Living & Comfort", children: [
      { name: "Cozy Vibes & Daily Routines" },
      { name: "Reading Nooks, Blankets & Tea" },
    ]},
    { name: "Relationships, Family & Friendship", children: [
      { name: "Couples & Love Moments" },
      { name: "Family Moments" },
      { name: "Friendships & Hangouts" },
    ]},
    { name: "Retro, Eras & Nostalgia", children: [
      { name: "Arcades, Diners & Roller Rinks" },
      { name: "Decades & Vintage Eras" },
      { name: "Life Before the Internet" },
    ]},
    { name: "Self-Care & Stress Relief", children: [
      { name: "Mindfulness & Calm Scenes" },
    ]},
    { name: "Travel, Towns & Cultural Scenes", children: [
      { name: "Cozy Japan & Street Scenes" },
      { name: "Small Towns, Markets & Shops" },
    ]},
  ]},
  { name: "Fantasy & Whimsical", children: [
    { name: "Dragons & Mythical Beasts", children: [
      { name: "Cozy & Baby Dragons" },
      { name: "Unicorns, Griffins & Phoenixes" },
    ]},
    { name: "Fairies, Elves & Gnomes", children: [
      { name: "Elves & Woodland Folk" },
      { name: "Garden & Forest Fairies" },
      { name: "Gnomes & Toadstool Homes" },
    ]},
    { name: "Kingdoms, Castles & Romantasy", children: [
      { name: "Enchanted Kingdoms & Quests" },
      { name: "Royal Palaces & Ballrooms" },
    ]},
    { name: "Witches, Alchemy & Magic", children: [
      { name: "Cozy Witches & Potion Shops" },
      { name: "Spellbooks, Crystals & Tarot" },
    ]},
  ]},
  { name: "Food & Treats", children: [
    { name: "Baked Goods, Pastries & Sweets", children: [
      { name: "Cakes, Cookies & Pies" },
      { name: "Ice Cream, Candy & Desserts" },
    ]},
    { name: "Drinks, Coffee & Tea", children: [
      { name: "Coffee Shops, Lattes & Matcha" },
      { name: "Tea Time & Herbal Brews" },
    ]},
    { name: "Meals, Produce & Pantry", children: [
      { name: "Comfort Food, Street Food & Snacks" },
      { name: "Fruits, Berries & Harvest" },
    ]},
  ]},
  { name: "Holidays & Seasons", children: [
    { name: "Christmas & Winter", children: [
      { name: "Christmas & Holiday Cheer" },
      { name: "Winter Snow & Ice Skating" },
    ]},
    { name: "Halloween & Autumn", children: [
      { name: "Autumn Harvest & Fall Foliage" },
      { name: "Spooky & Halloween" },
    ]},
    { name: "Spring & Easter", children: [
      { name: "Easter Bunnies & Eggs" },
      { name: "Spring Blossoms & Rain" },
    ]},
    { name: "Summer & Beach", children: [
      { name: "Beach Days & Poolside" },
      { name: "Summer Vacations & Sun" },
    ]},
  ]},
  { name: "Mandalas, Patterns & Abstract", children: [
    { name: "Borders, Frames & Stickers" },
    { name: "Floral & Nature Mandalas" },
    { name: "Geometric, Zentangle & Line Patterns" },
  ]},
  { name: "People & Characters", children: [
    { name: "Cozy Characters & Mascots", children: [
      { name: "Chibi & Cute Characters" },
      { name: "Everyday Characters" },
    ]},
    { name: "Fashion, Outfits & Portraits", children: [
      { name: "Character Portraits & Expressions" },
      { name: "Outfits & Wardrobe" },
    ]},
  ]},
  { name: "Workflow, Reference & Utility", children: [
    { name: "Color Palettes & Swatches" },
    { name: "Completed / Colored" },
    { name: "Multi-Theme / Mixed Books" },
    { name: "PDF Inbox" },
    { name: "Untagged" },
  ]},
];

const countNodes = (nodes) =>
  nodes.reduce((n, node) => n + 1 + (node.children ? countNodes(node.children) : 0), 0);

// Adds the default categories. Existing categories are never touched: a
// category is created only when no category with the same name under the
// same parent exists yet. Safe to run more than once — running it again
// only adds categories that are still missing.
export async function seedDefaultCategories() {
  const existing = await db.categories.toArray();
  const byKey = new Map(existing.map((c) => [`${c.parentId ?? ''}|${c.name}`, c.id]));
  let created = 0;

  const ensureNode = async (node, parentId = null) => {
    const key = `${parentId ?? ''}|${node.name}`;
    let id = byKey.get(key);
    if (id === undefined) {
      id = await db.categories.add({ name: node.name, parentId });
      byKey.set(key, id);
      created++;
    }
    if (node.children) {
      for (const child of node.children) {
        await ensureNode(child, id);
      }
    }
  };

  for (const rootNode of categoryTree) {
    await ensureNode(rootNode, null);
  }

  const total = countNodes(categoryTree);
  console.log(`✅ Default categories ready (${created} new, ${total - created} already existed).`);
  return { created, total };
}
