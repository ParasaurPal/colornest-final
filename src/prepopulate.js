import { db } from './db';

const categoryTree = [
  { name: "ANIMALS", children: [
    { name: "Mammals", children: [
      { name: "Canines", children: [{ name: "Dogs" }, { name: "Wolves" }, { name: "Foxes" }, { name: "Coyotes" }, { name: "Wild Dogs" }] },
      { name: "Felines", children: [{ name: "Domestic Cats" }, { name: "Lions" }, { name: "Tigers" }, { name: "Leopards" }, { name: "Cheetahs" }, { name: "Other Big Cats" }] },
      { name: "Bears" },
      { name: "Rodents", children: [{ name: "Squirrels" }, { name: "Hamsters" }, { name: "Guinea Pigs" }, { name: "Mice" }, { name: "Rats" }, { name: "Beavers" }] },
      { name: "Hoofed Mammals", children: [{ name: "Horses" }, { name: "Zebras" }, { name: "Deer" }, { name: "Moose" }, { name: "Cattle" }, { name: "Sheep" }, { name: "Goats" }, { name: "Pigs" }] },
      { name: "Primates", children: [{ name: "Monkeys" }, { name: "Apes" }, { name: "Lemurs" }] },
      { name: "Marine Mammals", children: [{ name: "Whales" }, { name: "Dolphins" }, { name: "Seals" }, { name: "Sea Lions" }, { name: "Otters" }] },
      { name: "Marsupials", children: [{ name: "Kangaroos" }, { name: "Koalas" }, { name: "Opossums" }, { name: "Wombats" }] },
      { name: "Other Mammals", children: [{ name: "Rabbits" }, { name: "Hares" }, { name: "Elephants" }, { name: "Giraffes" }, { name: "Rhinoceroses" }, { name: "Hippopotamuses" }, { name: "Bats" }, { name: "Sloths" }, { name: "Hedgehogs" }] }
    ]},
    { name: "Birds", children: [{ name: "Songbirds" }, { name: "Parrots" }, { name: "Owls" }, { name: "Birds of Prey" }, { name: "Waterfowl" }, { name: "Penguins" }, { name: "Flightless Birds" }, { name: "Tropical Birds" }, { name: "Poultry" }, { name: "Other Birds" }] },
    { name: "Reptiles", children: [{ name: "Snakes" }, { name: "Lizards" }, { name: "Turtles" }, { name: "Tortoises" }, { name: "Crocodilians" }, { name: "Chameleons" }] },
    { name: "Amphibians", children: [{ name: "Frogs" }, { name: "Toads" }, { name: "Salamanders" }, { name: "Newts" }] },
    { name: "Fish", children: [{ name: "Freshwater Fish" }, { name: "Saltwater Fish" }, { name: "Tropical Fish" }, { name: "Sharks" }, { name: "Rays" }, { name: "Deep-Sea Fish" }] },
    { name: "Invertebrates", children: [
      { name: "Insects", children: [{ name: "Butterflies" }, { name: "Moths" }, { name: "Bees" }, { name: "Wasps" }, { name: "Beetles" }, { name: "Dragonflies" }, { name: "Ladybugs" }, { name: "Ants" }, { name: "Grasshoppers" }] },
      { name: "Arachnids", children: [{ name: "Spiders" }, { name: "Scorpions" }, { name: "Ticks" }] },
      { name: "Mollusks", children: [{ name: "Octopuses" }, { name: "Squids" }, { name: "Snails" }, { name: "Slugs" }, { name: "Clams" }] },
      { name: "Crustaceans", children: [{ name: "Crabs" }, { name: "Lobsters" }, { name: "Shrimp" }, { name: "Crayfish" }] },
      { name: "Worms" }, { name: "Jellyfish" }, { name: "Coral" }, { name: "Starfish" }
    ]},
    { name: "Prehistoric Animals", children: [
      { name: "Dinosaurs", children: [{ name: "Theropods" }, { name: "Sauropods" }, { name: "Ceratopsians" }, { name: "Hadrosaurs" }, { name: "Ankylosaurs" }, { name: "Stegosaurs" }, { name: "Raptors" }] },
      { name: "Prehistoric Mammals" }, { name: "Marine Reptiles" }, { name: "Pterosaurs" }, { name: "Ice Age Animals" }, { name: "Extinct Animals" }
    ]},
    { name: "Mythical Animals", children: [{ name: "Dragons" }, { name: "Unicorns" }, { name: "Phoenixes" }, { name: "Griffins" }, { name: "Pegasi" }, { name: "Sea Serpents" }, { name: "Hybrid Creatures" }] },
    { name: "Pets", children: [{ name: "Dogs" }, { name: "Cats" }, { name: "Birds" }, { name: "Fish" }, { name: "Small Pets" }, { name: "Reptile Pets" }] },
    { name: "Farm Animals" }, { name: "Zoo Animals" }, { name: "Wildlife" }
  ]},
  { name: "PEOPLE", children: [
    { name: "Adults", children: [{ name: "Men" }, { name: "Women" }, { name: "Couples" }, { name: "Groups" }] },
    { name: "Children", children: [{ name: "Boys" }, { name: "Girls" }, { name: "Babies" }, { name: "Toddlers" }] },
    { name: "Families", children: [{ name: "Parents" }, { name: "Siblings" }, { name: "Grandparents" }, { name: "Family Activities" }] },
    { name: "Occupations", children: [{ name: "Medical Workers" }, { name: "Teachers" }, { name: "Artists" }, { name: "Scientists" }, { name: "Chefs" }, { name: "Farmers" }, { name: "Construction Workers" }, { name: "First Responders" }, { name: "Military" }, { name: "Astronauts" }] },
    { name: "Historical People" }, { name: "Cultural Clothing" },
    { name: "Fashion", children: [{ name: "Casual Wear" }, { name: "Formal Wear" }, { name: "Dresses" }, { name: "Costumes" }, { name: "Accessories" }, { name: "Shoes" }, { name: "Hairstyles" }, { name: "Makeup" }] },
    { name: "Activities", children: [{ name: "Sports" }, { name: "Dancing" }, { name: "Music" }, { name: "Reading" }, { name: "Hobbies" }, { name: "Gardening" }, { name: "Cooking" }] },
    { name: "Portraits" }, { name: "Silhouettes" }, { name: "Fantasy Characters" }
  ]},
  { name: "PLANTS & FLOWERS", children: [
    { name: "Flowers", children: [{ name: "Roses" }, { name: "Sunflowers" }, { name: "Tulips" }, { name: "Daisies" }, { name: "Lilies" }, { name: "Orchids" }, { name: "Wildflowers" }, { name: "Tropical Flowers" }, { name: "Flower Bouquets" }] },
    { name: "Trees", children: [{ name: "Deciduous Trees" }, { name: "Evergreen Trees" }, { name: "Fruit Trees" }, { name: "Tropical Trees" }, { name: "Bonsai" }] },
    { name: "Shrubs & Bushes" }, { name: "Leaves & Foliage" }, { name: "Vines & Ivy" }, { name: "Grasses" }, { name: "Ferns" }, { name: "Mosses" }, { name: "Cacti" }, { name: "Succulents" }, { name: "Mushrooms & Fungi" }, { name: "Houseplants" }, { name: "Aquatic Plants" }, { name: "Carnivorous Plants" }, { name: "Herbs" }, { name: "Botanical Illustrations" }
  ]},
  { name: "NATURE & LANDSCAPES", children: [
    { name: "Forests" }, { name: "Jungles" }, { name: "Mountains" }, { name: "Deserts" }, { name: "Beaches" }, { name: "Oceans" }, { name: "Rivers & Streams" }, { name: "Lakes & Ponds" }, { name: "Waterfalls" }, { name: "Caves" }, { name: "Islands" }, { name: "Grasslands" }, { name: "Wetlands" }, { name: "Arctic & Antarctic" }, { name: "Gardens" }, { name: "Countryside" }, { name: "Natural Wonders" },
    { name: "Seasons", children: [{ name: "Spring" }, { name: "Summer" }, { name: "Autumn" }, { name: "Winter" }] }
  ]},
  { name: "FOOD & DRINKS", children: [
    { name: "Fruits" }, { name: "Vegetables" }, { name: "Berries" }, { name: "Nuts & Seeds" }, { name: "Bread & Bakery" }, { name: "Cakes" }, { name: "Cupcakes" }, { name: "Cookies" }, { name: "Candy & Sweets" }, { name: "Chocolate" }, { name: "Ice Cream" }, { name: "Desserts" }, { name: "Breakfast Foods" }, { name: "Lunch Foods" }, { name: "Dinner Foods" }, { name: "Fast Food" }, { name: "International Cuisine" }, { name: "Snacks" },
    { name: "Beverages", children: [{ name: "Coffee" }, { name: "Tea" }, { name: "Juice" }, { name: "Smoothies" }, { name: "Milkshakes" }, { name: "Soda" }] }
  ]},
  { name: "FANTASY & MYTHOLOGY", children: [
    { name: "Fantasy Creatures" }, { name: "Dragons" }, { name: "Fairies" }, { name: "Mermaids" }, { name: "Unicorns" }, { name: "Elves" }, { name: "Goblins" }, { name: "Trolls" }, { name: "Giants" }, { name: "Witches & Wizards" }, { name: "Magical Objects" }, { name: "Enchanted Forests" }, { name: "Fairy Houses" }, { name: "Castles" }, { name: "Mythological Gods" }, { name: "Mythological Heroes" }, { name: "Mythological Stories" }, { name: "Magical Worlds" }
  ]},
  { name: "HOLIDAYS & CELEBRATIONS", children: [
    { name: "Christmas" }, { name: "Halloween" }, { name: "Easter" }, { name: "Valentine's Day" }, { name: "St. Patrick's Day" }, { name: "Thanksgiving" }, { name: "New Year's" }, { name: "Independence Day" }, { name: "Mother's Day" }, { name: "Father's Day" }, { name: "Birthdays" }, { name: "Weddings" }, { name: "Anniversaries" }, { name: "Baby Showers" }, { name: "Graduation" }, { name: "Other Celebrations" }
  ]},
  { name: "TRANSPORTATION & VEHICLES", children: [
    { name: "Cars" }, { name: "Trucks" }, { name: "Motorcycles" }, { name: "Bicycles" }, { name: "Buses" }, { name: "Trains" }, { name: "Airplanes" }, { name: "Helicopters" }, { name: "Boats" }, { name: "Ships" }, { name: "Submarines" }, { name: "Construction Vehicles" }, { name: "Emergency Vehicles" }, { name: "Spacecraft" }, { name: "Fantasy Vehicles" }
  ]},
  { name: "BUILDINGS & ARCHITECTURE", children: [
    { name: "Houses" }, { name: "Apartments" }, { name: "Cottages" }, { name: "Mansions" }, { name: "Castles" }, { name: "Churches & Temples" }, { name: "Schools" }, { name: "Libraries" }, { name: "Hospitals" }, { name: "Shops & Stores" }, { name: "Restaurants" }, { name: "Historical Buildings" }, { name: "City Buildings" }, { name: "Bridges" }, { name: "Lighthouses" },
    { name: "Interior Rooms", children: [{ name: "Bedrooms" }, { name: "Bathrooms" }, { name: "Kitchens" }, { name: "Living Rooms" }, { name: "Offices" }] }
  ]},
  { name: "OBJECTS & EVERYDAY ITEMS", children: [
    { name: "Household Items" }, { name: "Furniture" }, { name: "Kitchenware" }, { name: "Electronics" }, { name: "Technology" }, { name: "Tools" }, { name: "Toys" }, { name: "Games" }, { name: "Books" }, { name: "Stationery" }, { name: "Art Supplies" }, { name: "Musical Instruments" }, { name: "Jewelry" }, { name: "Watches" }, { name: "Bags & Purses" }, { name: "Clothing" }, { name: "Shoes" }, { name: "Cosmetics" }, { name: "Gardening Supplies" }, { name: "Sports Equipment" }
  ]},
  { name: "SPACE & ASTRONOMY", children: [
    { name: "Planets" }, { name: "Stars" }, { name: "Constellations" }, { name: "Galaxies" }, { name: "Nebulas" }, { name: "Moons" }, { name: "Solar System" }, { name: "Sun" }, { name: "Comets" }, { name: "Asteroids" }, { name: "Meteors" }, { name: "Black Holes" }, { name: "Astronauts" }, { name: "Space Exploration" }, { name: "Aliens" }, { name: "UFOs" }
  ]},
  { name: "UNDERWATER & MARINE LIFE", children: [
    { name: "Coral Reefs" }, { name: "Ocean Floors" }, { name: "Underwater Landscapes" }, { name: "Sea Creatures" }, { name: "Shipwrecks" }, { name: "Underwater Plants" }, { name: "Mermaids" }, { name: "Aquariums" }, { name: "Ocean Conservation" }
  ]},
  { name: "ART, PATTERNS & DESIGNS", children: [
    { name: "Mandalas" }, { name: "Zentangle" }, { name: "Geometric Patterns" }, { name: "Abstract Art" }, { name: "Doodles" }, { name: "Repeating Patterns" }, { name: "Floral Patterns" }, { name: "Animal Patterns" }, { name: "Paisley" }, { name: "Mosaic" }, { name: "Stained Glass" }, { name: "Symmetry Designs" }, { name: "Optical Illusions" }, { name: "Decorative Borders" }, { name: "Intricate Patterns" }
  ]},
  { name: "HORROR & SUPERNATURAL", children: [
    { name: "Witches" }, { name: "Vampires" }, { name: "Werewolves" }, { name: "Zombies" }, { name: "Ghosts" }, { name: "Skeletons" }, { name: "Monsters" }, { name: "Demons" }, { name: "Angels" }, { name: "Haunted Houses" }, { name: "Gothic Architecture" }, { name: "Dark Fantasy" }, { name: "Supernatural Scenes" }, { name: "Cryptids" }
  ]},
  { name: "ACTIVITIES, HOBBIES & SPORTS", children: [
    { name: "Sports", children: [{ name: "Football" }, { name: "Basketball" }, { name: "Baseball" }, { name: "Soccer" }, { name: "Tennis" }, { name: "Volleyball" }, { name: "Golf" }, { name: "Swimming" }, { name: "Gymnastics" }, { name: "Skateboarding" }] },
    { name: "Arts & Crafts" }, { name: "Painting & Drawing" }, { name: "Knitting & Sewing" }, { name: "Reading" }, { name: "Music" }, { name: "Dancing" }, { name: "Gardening" }, { name: "Cooking & Baking" }, { name: "Camping" }, { name: "Fishing" }, { name: "Hiking" }, { name: "Travel" }, { name: "Collecting" }
  ]},
  { name: "EDUCATIONAL & LEARNING", children: [
    { name: "Alphabet" }, { name: "Numbers" }, { name: "Shapes" }, { name: "Colors" }, { name: "Counting" }, { name: "Mathematics" }, { name: "Science" }, { name: "Biology" }, { name: "Anatomy" }, { name: "Geography" }, { name: "History" }, { name: "Solar System" }, { name: "Dinosaurs" }, { name: "Maps" }, { name: "Learning Activities" }, { name: "Educational Worksheets" }
  ]},
  { name: "PLACES & TRAVEL", children: [
    { name: "Cities" }, { name: "Villages" }, { name: "Countries" }, { name: "Famous Landmarks" }, { name: "Historical Sites" }, { name: "Amusement Parks" }, { name: "Zoos" }, { name: "Museums" }, { name: "Theme Parks" }, { name: "Campgrounds" }, { name: "Tourist Attractions" }
  ]},
  { name: "SEASONAL & WEATHER", children: [
    { name: "Springtime" }, { name: "Summer Activities" }, { name: "Autumn Activities" }, { name: "Winter Activities" }, { name: "Rain" }, { name: "Snow" }, { name: "Thunderstorms" }, { name: "Rainbows" }, { name: "Clouds" }, { name: "Sunsets" }, { name: "Sunrises" }, { name: "Wind" }, { name: "Seasonal Decorations" }
  ]},
  { name: "SPIRITUALITY & SYMBOLISM", children: [
    { name: "Zodiac Signs" }, { name: "Astrology" }, { name: "Tarot" }, { name: "Runes" }, { name: "Crystals" }, { name: "Gemstones" }, { name: "Spiritual Symbols" }, { name: "Religious Symbols" }, { name: "Sacred Geometry" }, { name: "Meditation" }, { name: "Chakras" }, { name: "Mystical Objects" }
  ]},
  { name: "LETTERS, WORDS & NUMBERS", children: [
    { name: "Alphabet Letters" }, { name: "Typography" }, { name: "Inspirational Quotes" }, { name: "Motivational Quotes" }, { name: "Positive Affirmations" }, { name: "Names" }, { name: "Numbers" }, { name: "Word Art" }, { name: "Decorative Lettering" }, { name: "Handwriting Practice" }
  ]}
];

export async function seedMassiveCategories() {
  await db.categories.clear(); // Wipe old categories

  const insertNode = async (node, parentId = null) => {
    const id = await db.categories.add({ name: node.name, parentId: parentId });
    if (node.children) {
      for (const child of node.children) {
        await insertNode(child, id);
      }
    }
  };

  for (const rootNode of categoryTree) {
    await insertNode(rootNode, null);
  }
  
  console.log("✅ Massive category tree populated!");
}
