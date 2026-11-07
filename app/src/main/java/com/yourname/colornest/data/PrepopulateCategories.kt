package com.yourname.colornest.data

import com.yourname.colornest.data.dao.CategoryDao
import com.yourname.colornest.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CategoryNode(
    val name: String,
    val children: List<CategoryNode> = emptyList()
)

object PrepopulateCategories {

    val massiveCategoryTree: List<CategoryNode> = listOf(
        CategoryNode(
            "ANIMALS", listOf(
                CategoryNode(
                    "Mammals", listOf(
                        CategoryNode("Canines", listOf(CategoryNode("Dogs"), CategoryNode("Wolves"), CategoryNode("Foxes"), CategoryNode("Coyotes"), CategoryNode("Wild Dogs"))),
                        CategoryNode("Felines", listOf(CategoryNode("Domestic Cats"), CategoryNode("Lions"), CategoryNode("Tigers"), CategoryNode("Leopards"), CategoryNode("Cheetahs"), CategoryNode("Other Big Cats"))),
                        CategoryNode("Bears"),
                        CategoryNode("Rodents", listOf(CategoryNode("Squirrels"), CategoryNode("Hamsters"), CategoryNode("Guinea Pigs"), CategoryNode("Mice"), CategoryNode("Rats"), CategoryNode("Beavers"))),
                        CategoryNode("Hoofed Mammals", listOf(CategoryNode("Horses"), CategoryNode("Zebras"), CategoryNode("Deer"), CategoryNode("Moose"), CategoryNode("Cattle"), CategoryNode("Sheep"), CategoryNode("Goats"), CategoryNode("Pigs"))),
                        CategoryNode("Primates", listOf(CategoryNode("Monkeys"), CategoryNode("Apes"), CategoryNode("Lemurs"))),
                        CategoryNode("Marine Mammals", listOf(CategoryNode("Whales"), CategoryNode("Dolphins"), CategoryNode("Seals"), CategoryNode("Sea Lions"), CategoryNode("Otters"))),
                        CategoryNode("Marsupials", listOf(CategoryNode("Kangaroos"), CategoryNode("Koalas"), CategoryNode("Opossums"), CategoryNode("Wombats"))),
                        CategoryNode("Other Mammals", listOf(CategoryNode("Rabbits"), CategoryNode("Hares"), CategoryNode("Elephants"), CategoryNode("Giraffes"), CategoryNode("Rhinoceroses"), CategoryNode("Hippopotamuses"), CategoryNode("Bats"), CategoryNode("Sloths"), CategoryNode("Hedgehogs")))
                    )
                ),
                CategoryNode("Birds", listOf(CategoryNode("Songbirds"), CategoryNode("Parrots"), CategoryNode("Owls"), CategoryNode("Birds of Prey"), CategoryNode("Waterfowl"), CategoryNode("Penguins"), CategoryNode("Flightless Birds"), CategoryNode("Tropical Birds"), CategoryNode("Poultry"), CategoryNode("Other Birds"))),
                CategoryNode("Reptiles", listOf(CategoryNode("Snakes"), CategoryNode("Lizards"), CategoryNode("Turtles"), CategoryNode("Tortoises"), CategoryNode("Crocodilians"), CategoryNode("Chameleons"))),
                CategoryNode("Amphibians", listOf(CategoryNode("Frogs"), CategoryNode("Toads"), CategoryNode("Salamanders"), CategoryNode("Newts"))),
                CategoryNode("Fish", listOf(CategoryNode("Freshwater Fish"), CategoryNode("Saltwater Fish"), CategoryNode("Tropical Fish"), CategoryNode("Sharks"), CategoryNode("Rays"), CategoryNode("Deep-Sea Fish"))),
                CategoryNode(
                    "Invertebrates", listOf(
                        CategoryNode("Insects", listOf(CategoryNode("Butterflies"), CategoryNode("Moths"), CategoryNode("Bees"), CategoryNode("Wasps"), CategoryNode("Beetles"), CategoryNode("Dragonflies"), CategoryNode("Ladybugs"), CategoryNode("Ants"), CategoryNode("Grasshoppers"))),
                        CategoryNode("Arachnids", listOf(CategoryNode("Spiders"), CategoryNode("Scorpions"), CategoryNode("Ticks"))),
                        CategoryNode("Mollusks", listOf(CategoryNode("Octopuses"), CategoryNode("Squids"), CategoryNode("Snails"), CategoryNode("Slugs"), CategoryNode("Clams"))),
                        CategoryNode("Crustaceans", listOf(CategoryNode("Crabs"), CategoryNode("Lobsters"), CategoryNode("Shrimp"), CategoryNode("Crayfish"))),
                        CategoryNode("Worms"), CategoryNode("Jellyfish"), CategoryNode("Coral"), CategoryNode("Starfish")
                    )
                ),
                CategoryNode(
                    "Prehistoric Animals", listOf(
                        CategoryNode("Dinosaurs", listOf(CategoryNode("Theropods"), CategoryNode("Sauropods"), CategoryNode("Ceratopsians"), CategoryNode("Hadrosaurs"), CategoryNode("Ankylosaurs"), CategoryNode("Stegosaurs"), CategoryNode("Raptors"))),
                        CategoryNode("Prehistoric Mammals"), CategoryNode("Marine Reptiles"), CategoryNode("Pterosaurs"), CategoryNode("Ice Age Animals"), CategoryNode("Extinct Animals")
                    )
                ),
                CategoryNode("Mythical Animals", listOf(CategoryNode("Dragons"), CategoryNode("Unicorns"), CategoryNode("Phoenixes"), CategoryNode("Griffins"), CategoryNode("Pegasi"), CategoryNode("Sea Serpents"), CategoryNode("Hybrid Creatures"))),
                CategoryNode("Pets", listOf(CategoryNode("Dogs"), CategoryNode("Cats"), CategoryNode("Birds"), CategoryNode("Fish"), CategoryNode("Small Pets"), CategoryNode("Reptile Pets"))),
                CategoryNode("Farm Animals"), CategoryNode("Zoo Animals"), CategoryNode("Wildlife")
            )
        ),
        CategoryNode(
            "PEOPLE", listOf(
                CategoryNode("Adults", listOf(CategoryNode("Men"), CategoryNode("Women"), CategoryNode("Couples"), CategoryNode("Groups"))),
                CategoryNode("Children", listOf(CategoryNode("Boys"), CategoryNode("Girls"), CategoryNode("Babies"), CategoryNode("Toddlers"))),
                CategoryNode("Families", listOf(CategoryNode("Parents"), CategoryNode("Siblings"), CategoryNode("Grandparents"), CategoryNode("Family Activities"))),
                CategoryNode("Occupations", listOf(CategoryNode("Medical Workers"), CategoryNode("Teachers"), CategoryNode("Artists"), CategoryNode("Scientists"), CategoryNode("Chefs"), CategoryNode("Farmers"), CategoryNode("Construction Workers"), CategoryNode("First Responders"), CategoryNode("Military"), CategoryNode("Astronauts"))),
                CategoryNode("Historical People"), CategoryNode("Cultural Clothing"),
                CategoryNode("Fashion", listOf(CategoryNode("Casual Wear"), CategoryNode("Formal Wear"), CategoryNode("Dresses"), CategoryNode("Costumes"), CategoryNode("Accessories"), CategoryNode("Shoes"), CategoryNode("Hairstyles"), CategoryNode("Makeup"))),
                CategoryNode("Activities", listOf(CategoryNode("Sports"), CategoryNode("Dancing"), CategoryNode("Music"), CategoryNode("Reading"), CategoryNode("Hobbies"), CategoryNode("Gardening"), CategoryNode("Cooking"))),
                CategoryNode("Portraits"), CategoryNode("Silhouettes"), CategoryNode("Fantasy Characters")
            )
        ),
        CategoryNode(
            "PLANTS & FLOWERS", listOf(
                CategoryNode("Flowers", listOf(CategoryNode("Roses"), CategoryNode("Sunflowers"), CategoryNode("Tulips"), CategoryNode("Daisies"), CategoryNode("Lilies"), CategoryNode("Orchids"), CategoryNode("Wildflowers"), CategoryNode("Tropical Flowers"), CategoryNode("Flower Bouquets"))),
                CategoryNode("Trees", listOf(CategoryNode("Deciduous Trees"), CategoryNode("Evergreen Trees"), CategoryNode("Fruit Trees"), CategoryNode("Tropical Trees"), CategoryNode("Bonsai"))),
                CategoryNode("Shrubs & Bushes"), CategoryNode("Leaves & Foliage"), CategoryNode("Vines & Ivy"), CategoryNode("Grasses"), CategoryNode("Ferns"), CategoryNode("Mosses"), CategoryNode("Cacti"), CategoryNode("Succulents"), CategoryNode("Mushrooms & Fungi"), CategoryNode("Houseplants"), CategoryNode("Aquatic Plants"), CategoryNode("Carnivorous Plants"), CategoryNode("Herbs"), CategoryNode("Botanical Illustrations")
            )
        ),
        CategoryNode(
            "NATURE & LANDSCAPES", listOf(
                CategoryNode("Forests"), CategoryNode("Jungles"), CategoryNode("Mountains"), CategoryNode("Deserts"), CategoryNode("Beaches"), CategoryNode("Oceans"), CategoryNode("Rivers & Streams"), CategoryNode("Lakes & Ponds"), CategoryNode("Waterfalls"), CategoryNode("Caves"), CategoryNode("Islands"), CategoryNode("Grasslands"), CategoryNode("Wetlands"), CategoryNode("Arctic & Antarctic"), CategoryNode("Gardens"), CategoryNode("Countryside"), CategoryNode("Natural Wonders"),
                CategoryNode("Seasons", listOf(CategoryNode("Spring"), CategoryNode("Summer"), CategoryNode("Autumn"), CategoryNode("Winter")))
            )
        ),
        CategoryNode(
            "FOOD & DRINKS", listOf(
                CategoryNode("Fruits"), CategoryNode("Vegetables"), CategoryNode("Berries"), CategoryNode("Nuts & Seeds"), CategoryNode("Bread & Bakery"), CategoryNode("Cakes"), CategoryNode("Cupcakes"), CategoryNode("Cookies"), CategoryNode("Candy & Sweets"), CategoryNode("Chocolate"), CategoryNode("Ice Cream"), CategoryNode("Desserts"), CategoryNode("Breakfast Foods"), CategoryNode("Lunch Foods"), CategoryNode("Dinner Foods"), CategoryNode("Fast Food"), CategoryNode("International Cuisine"), CategoryNode("Snacks"),
                CategoryNode("Beverages", listOf(CategoryNode("Coffee"), CategoryNode("Tea"), CategoryNode("Juice"), CategoryNode("Smoothies"), CategoryNode("Milkshakes"), CategoryNode("Soda")))
            )
        ),
        CategoryNode(
            "FANTASY & MYTHOLOGY", listOf(
                CategoryNode("Fantasy Creatures"), CategoryNode("Dragons"), CategoryNode("Fairies"), CategoryNode("Mermaids"), CategoryNode("Unicorns"), CategoryNode("Elves"), CategoryNode("Goblins"), CategoryNode("Trolls"), CategoryNode("Giants"), CategoryNode("Witches & Wizards"), CategoryNode("Magical Objects"), CategoryNode("Enchanted Forests"), CategoryNode("Fairy Houses"), CategoryNode("Castles"), CategoryNode("Mythological Gods"), CategoryNode("Mythological Heroes"), CategoryNode("Mythological Stories"), CategoryNode("Magical Worlds")
            )
        ),
        CategoryNode(
            "HOLIDAYS & CELEBRATIONS", listOf(
                CategoryNode("Christmas"), CategoryNode("Halloween"), CategoryNode("Easter"), CategoryNode("Valentine's Day"), CategoryNode("St. Patrick's Day"), CategoryNode("Thanksgiving"), CategoryNode("New Year's"), CategoryNode("Independence Day"), CategoryNode("Mother's Day"), CategoryNode("Father's Day"), CategoryNode("Birthdays"), CategoryNode("Weddings"), CategoryNode("Anniversaries"), CategoryNode("Baby Showers"), CategoryNode("Graduation"), CategoryNode("Other Celebrations")
            )
        ),
        CategoryNode(
            "TRANSPORTATION & VEHICLES", listOf(
                CategoryNode("Cars"), CategoryNode("Trucks"), CategoryNode("Motorcycles"), CategoryNode("Bicycles"), CategoryNode("Buses"), CategoryNode("Trains"), CategoryNode("Airplanes"), CategoryNode("Helicopters"), CategoryNode("Boats"), CategoryNode("Ships"), CategoryNode("Submarines"), CategoryNode("Construction Vehicles"), CategoryNode("Emergency Vehicles"), CategoryNode("Spacecraft"), CategoryNode("Fantasy Vehicles")
            )
        ),
        CategoryNode(
            "BUILDINGS & ARCHITECTURE", listOf(
                CategoryNode("Houses"), CategoryNode("Apartments"), CategoryNode("Cottages"), CategoryNode("Mansions"), CategoryNode("Castles"), CategoryNode("Churches & Temples"), CategoryNode("Schools"), CategoryNode("Libraries"), CategoryNode("Hospitals"), CategoryNode("Shops & Stores"), CategoryNode("Restaurants"), CategoryNode("Historical Buildings"), CategoryNode("City Buildings"), CategoryNode("Bridges"), CategoryNode("Lighthouses"),
                CategoryNode("Interior Rooms", listOf(CategoryNode("Bedrooms"), CategoryNode("Bathrooms"), CategoryNode("Kitchens"), CategoryNode("Living Rooms"), CategoryNode("Offices")))
            )
        ),
        CategoryNode(
            "OBJECTS & EVERYDAY ITEMS", listOf(
                CategoryNode("Household Items"), CategoryNode("Furniture"), CategoryNode("Kitchenware"), CategoryNode("Electronics"), CategoryNode("Technology"), CategoryNode("Tools"), CategoryNode("Toys"), CategoryNode("Games"), CategoryNode("Books"), CategoryNode("Stationery"), CategoryNode("Art Supplies"), CategoryNode("Musical Instruments"), CategoryNode("Jewelry"), CategoryNode("Watches"), CategoryNode("Bags & Purses"), CategoryNode("Clothing"), CategoryNode("Shoes"), CategoryNode("Cosmetics"), CategoryNode("Gardening Supplies"), CategoryNode("Sports Equipment")
            )
        ),
        CategoryNode(
            "SPACE & ASTRONOMY", listOf(
                CategoryNode("Planets"), CategoryNode("Stars"), CategoryNode("Constellations"), CategoryNode("Galaxies"), CategoryNode("Nebulas"), CategoryNode("Moons"), CategoryNode("Solar System"), CategoryNode("Sun"), CategoryNode("Comets"), CategoryNode("Asteroids"), CategoryNode("Meteors"), CategoryNode("Black Holes"), CategoryNode("Astronauts"), CategoryNode("Space Exploration"), CategoryNode("Aliens"), CategoryNode("UFOs")
            )
        ),
        CategoryNode(
            "UNDERWATER & MARINE LIFE", listOf(
                CategoryNode("Coral Reefs"), CategoryNode("Ocean Floors"), CategoryNode("Underwater Landscapes"), CategoryNode("Sea Creatures"), CategoryNode("Shipwrecks"), CategoryNode("Underwater Plants"), CategoryNode("Mermaids"), CategoryNode("Aquariums"), CategoryNode("Ocean Conservation")
            )
        ),
        CategoryNode(
            "ART, PATTERNS & DESIGNS", listOf(
                CategoryNode("Mandalas"), CategoryNode("Zentangle"), CategoryNode("Geometric Patterns"), CategoryNode("Abstract Art"), CategoryNode("Doodles"), CategoryNode("Repeating Patterns"), CategoryNode("Floral Patterns"), CategoryNode("Animal Patterns"), CategoryNode("Paisley"), CategoryNode("Mosaic"), CategoryNode("Stained Glass"), CategoryNode("Symmetry Designs"), CategoryNode("Optical Illusions"), CategoryNode("Decorative Borders"), CategoryNode("Intricate Patterns")
            )
        ),
        CategoryNode(
            "HORROR & SUPERNATURAL", listOf(
                CategoryNode("Witches"), CategoryNode("Vampires"), CategoryNode("Werewolves"), CategoryNode("Zombies"), CategoryNode("Ghosts"), CategoryNode("Skeletons"), CategoryNode("Monsters"), CategoryNode("Demons"), CategoryNode("Angels"), CategoryNode("Haunted Houses"), CategoryNode("Gothic Architecture"), CategoryNode("Dark Fantasy"), CategoryNode("Supernatural Scenes"), CategoryNode("Cryptids")
            )
        ),
        CategoryNode(
            "ACTIVITIES, HOBBIES & SPORTS", listOf(
                CategoryNode("Sports", listOf(CategoryNode("Football"), CategoryNode("Basketball"), CategoryNode("Baseball"), CategoryNode("Soccer"), CategoryNode("Tennis"), CategoryNode("Volleyball"), CategoryNode("Golf"), CategoryNode("Swimming"), CategoryNode("Gymnastics"), CategoryNode("Skateboarding"))),
                CategoryNode("Arts & Crafts"), CategoryNode("Painting & Drawing"), CategoryNode("Knitting & Sewing"), CategoryNode("Reading"), CategoryNode("Music"), CategoryNode("Dancing"), CategoryNode("Gardening"), CategoryNode("Cooking & Baking"), CategoryNode("Camping"), CategoryNode("Fishing"), CategoryNode("Hiking"), CategoryNode("Travel"), CategoryNode("Collecting")
            )
        ),
        CategoryNode(
            "EDUCATIONAL & LEARNING", listOf(
                CategoryNode("Alphabet"), CategoryNode("Numbers"), CategoryNode("Shapes"), CategoryNode("Colors"), CategoryNode("Counting"), CategoryNode("Mathematics"), CategoryNode("Science"), CategoryNode("Biology"), CategoryNode("Anatomy"), CategoryNode("Geography"), CategoryNode("History"), CategoryNode("Solar System"), CategoryNode("Dinosaurs"), CategoryNode("Maps"), CategoryNode("Learning Activities"), CategoryNode("Educational Worksheets")
            )
        ),
        CategoryNode(
            "PLACES & TRAVEL", listOf(
                CategoryNode("Cities"), CategoryNode("Villages"), CategoryNode("Countries"), CategoryNode("Famous Landmarks"), CategoryNode("Historical Sites"), CategoryNode("Amusement Parks"), CategoryNode("Zoos"), CategoryNode("Museums"), CategoryNode("Theme Parks"), CategoryNode("Campgrounds"), CategoryNode("Tourist Attractions")
            )
        ),
        CategoryNode(
            "SEASONAL & WEATHER", listOf(
                CategoryNode("Springtime"), CategoryNode("Summer Activities"), CategoryNode("Autumn Activities"), CategoryNode("Winter Activities"), CategoryNode("Rain"), CategoryNode("Snow"), CategoryNode("Thunderstorms"), CategoryNode("Rainbows"), CategoryNode("Clouds"), CategoryNode("Sunsets"), CategoryNode("Sunrises"), CategoryNode("Wind"), CategoryNode("Seasonal Decorations")
            )
        ),
        CategoryNode(
            "SPIRITUALITY & SYMBOLISM", listOf(
                CategoryNode("Zodiac Signs"), CategoryNode("Astrology"), CategoryNode("Tarot"), CategoryNode("Runes"), CategoryNode("Crystals"), CategoryNode("Gemstones"), CategoryNode("Spiritual Symbols"), CategoryNode("Religious Symbols"), CategoryNode("Sacred Geometry"), CategoryNode("Meditation"), CategoryNode("Chakras"), CategoryNode("Mystical Objects")
            )
        ),
        CategoryNode(
            "LETTERS, WORDS & NUMBERS", listOf(
                CategoryNode("Alphabet Letters"), CategoryNode("Typography"), CategoryNode("Inspirational Quotes"), CategoryNode("Motivational Quotes"), CategoryNode("Positive Affirmations"), CategoryNode("Names"), CategoryNode("Numbers"), CategoryNode("Word Art"), CategoryNode("Decorative Lettering"), CategoryNode("Handwriting Practice")
            )
        )
    )

    suspend fun seedMassiveCategories(categoryDao: CategoryDao) = withContext(Dispatchers.IO) {
        categoryDao.deleteAllCategories()

        suspend fun insertNode(node: CategoryNode, parentId: Long?) {
            val id = categoryDao.insertCategory(Category(name = node.name, parentId = parentId))
            for (child in node.children) {
                insertNode(child, id)
            }
        }

        for (rootNode in massiveCategoryTree) {
            insertNode(rootNode, null)
        }
    }
}
