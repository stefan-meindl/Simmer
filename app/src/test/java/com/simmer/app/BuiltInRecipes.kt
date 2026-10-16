package com.simmer.app

import com.simmer.app.domain.model.*

/**
 * Test fixtures representing the five built-in recipes from simmer-data.js.
 * These mirror the JavaScript RECIPES array so resolve() tests can use real data.
 */
object BuiltInRecipes {

    private val riceOptions = listOf(
        MethodOption("pot", "In a pot", "flame", "18 min", "Absorption method on the hob."),
        MethodOption("microwave", "Microwave", "zap", "12 min", "Hands-off, no pot to wash."),
        MethodOption("cooker", "Rice cooker", "timer", "30 min", "Switch it on and forget it."),
    )

    private fun riceMethod(label: String = "Rice", l: String = "In a pot") = Method(
        id = "rice", label = label,
        q = "How do you want to cook the rice?",
        options = riceOptions,
    )

    private fun riceSteps(needs: List<String>): List<Step> = listOf(
        Step("r-pot", "cook", "Cook the rice", 1080, "Put the {ri:rice} in a pot with the {wa:water} and a pinch of salt. Bring to the boil, cover and cook on the lowest heat for 12 minutes, then leave to rest off the heat for 6.", needs, "rice=pot"),
        Step("r-mw", "cook", "Microwave the rice", 720, "Put the {ri:rice} and {wa:water} in a large microwave-safe bowl. Cover loosely and cook on full power for 10 minutes, then let it stand for 2.", needs, "rice=microwave"),
        Step("r-rc", "cook", "Start the rice cooker", 1800, "Add the {ri:rice} and {wa:water} to the rice cooker, close the lid and switch it on. It keeps warm until you serve.", needs, "rice=cooker"),
    )

    fun chilli(): Recipe = Recipe(
        id = "chilli", title = "Chilli con carne", category = "Mains",
        tags = listOf("Spicy", "One-pot", "Make-ahead"), time = 90, difficulty = "Easy", base = 4, icon = "flame",
        summary = "A slow-simmered chilli with kidney beans and smoked paprika, served over rice.",
        variations = listOf(
            Variation("mince", "swap", "Mince", listOf(Option("beef", "Beef"), Option("plant", "Plant-based")), "beef"),
            Variation("choc", "add", "Dark chocolate", emptyList(), "", "Adds depth and rounds off the heat.", "dc"),
            Variation("beer", "add", "Dark beer", emptyList(), "", "Replaces half the stock for a malty sauce.", "be"),
            Variation("cin", "add", "Cinnamon", emptyList(), "", "A warm note in the background.", "cn"),
        ),
        methods = listOf(
            Method("chilli", "The chilli", "How do you want to simmer the chilli?", listOf(
                MethodOption("pot", "Pot on the hob", "flame", "1 h", "Simmer gently, stirring now and then."),
                MethodOption("oven", "In the oven", "layers", "2 h", "Covered at 150°C. Richer, no stirring."),
                MethodOption("slow", "Slow cooker", "timer", "6 h", "Brown first, then leave it on low."),
            )),
            riceMethod(),
        ),
        ingredients = listOf(
            Ingredient("oi", "Olive oil", 2.0, "tbsp", aisle = "Pantry"),
            Ingredient("on", "Onions", 2.0, "", note = "finely chopped", aisle = "Produce"),
            Ingredient("pe", "Red pepper", 1.0, "", note = "diced", aisle = "Produce"),
            Ingredient("ga", "Garlic", 3.0, "clove", "cloves", note = "crushed", aisle = "Produce"),
            Ingredient("mb", "Beef mince", 500.0, "g", ifCondition = "mince=beef", aisle = "Meat"),
            Ingredient("mp", "Plant-based mince", 400.0, "g", ifCondition = "mince=plant", aisle = "Chilled"),
            Ingredient("cu", "Ground cumin", 2.0, "tsp", aisle = "Spices"),
            Ingredient("pa", "Smoked paprika", 1.0, "tsp", aisle = "Spices"),
            Ingredient("ch", "Chilli flakes", 1.0, "tsp", aisle = "Spices"),
            Ingredient("or", "Dried oregano", 1.0, "tsp", aisle = "Spices"),
            Ingredient("cn", "Ground cinnamon", 0.5, "tsp", ifCondition = "add:cin", aisle = "Spices"),
            Ingredient("tp", "Tomato purée", 2.0, "tbsp", aisle = "Tins & jars"),
            Ingredient("to", "Chopped tomatoes", 800.0, "g", note = "2 tins", aisle = "Tins & jars"),
            Ingredient("kb", "Kidney beans", 400.0, "g", note = "1 tin", aisle = "Tins & jars"),
            Ingredient("st", "Stock", 300.0, "ml", aisle = "Pantry",
                alt = listOf("add:beer" to 150.0)),
            Ingredient("be", "Dark beer", 200.0, "ml", ifCondition = "add:beer", aisle = "Drinks"),
            Ingredient("dc", "Dark chocolate", 20.0, "g", note = "70% cocoa", ifCondition = "add:choc", aisle = "Baking"),
            Ingredient("ri", "Long-grain rice", 300.0, "g", aisle = "Pantry"),
            Ingredient("wa", "Water", 450.0, "ml", note = "for the rice",
                alt = listOf("rice=microwave" to 600.0)),
            Ingredient("cr", "Soured cream", 150.0, "ml", note = "to serve", aisle = "Dairy"),
            Ingredient("co", "Coriander", 1.0, "bunch", "bunches", note = "to serve", aisle = "Produce"),
        ),
        steps = listOf(
            Step("p-veg", "prep", "Chop the vegetables", 600, "Finely chop the {on:onions}, dice the {pe:red pepper} and crush the {ga:garlic}. Keep them in separate piles."),
            Step("p-spice", "prep", "Mix the spices", 120, "Stir the {cu:cumin}, {pa:smoked paprika}, {ch:chilli flakes} and {or:oregano} together in a small bowl.", ifCondition = "!add:cin"),
            Step("p-spice", "prep", "Mix the spices", 120, "Stir the {cu:cumin}, {pa:smoked paprika}, {ch:chilli flakes}, {or:oregano} and {cn:cinnamon} together in a small bowl.", ifCondition = "add:cin"),
            Step("p-beans", "prep", "Drain the beans", 60, "Tip the {kb:kidney beans} into a sieve and rinse under cold water."),
            Step("p-choc", "prep", "Chop the chocolate", 60, "Roughly chop the {dc:dark chocolate} so it melts evenly later.", ifCondition = "add:choc"),
            Step("p-rice", "prep", "Rinse the rice", 120, "Rinse the {ri:rice} in a sieve until the water runs clear, then leave it to drain."),
            Step("c-soft", "cook", "Soften the onions", 480, "Heat the {oi:olive oil} in a large pot over a medium heat. Add the {on:onions} and {pe:pepper} and cook until soft. Add the {ga:garlic} for the last minute.", listOf("p-veg")),
            Step("c-brown", "cook", "Brown the mince", 480, "Turn up the heat, add the {mb:beef mince} and break it up with a spoon until browned all over.", ifCondition = "mince=beef"),
            Step("c-brown", "cook", "Brown the mince", 300, "Add the {mp:plant-based mince} and break it up with a spoon until lightly browned.", ifCondition = "mince=plant"),
            Step("c-spice", "cook", "Toast the spices", 60, "Stir in the spice mix and the {tp:tomato purée}. Cook for a minute until fragrant.", listOf("p-spice")),
            Step("c-liquid", "cook", "Add the liquids", 120, "Pour in the {to:chopped tomatoes} and {st:stock}, then add the {kb:kidney beans}. Stir and bring to a simmer.", listOf("p-beans"), "!add:beer"),
            Step("c-liquid", "cook", "Add the liquids", 240, "Pour in the {be:dark beer} and let it bubble for 2 minutes. Add the {to:chopped tomatoes}, {st:stock} and {kb:kidney beans}, then bring to a simmer.", listOf("p-beans"), "add:beer"),
            Step("c-sim", "cook", "Simmer the chilli", 3600, "Partly cover and simmer gently for an hour, stirring now and then. Add a splash of water if it gets too thick.", ifCondition = "chilli=pot"),
            Step("c-sim", "cook", "Braise in the oven", 7200, "Cover the pot with a lid and move it to the oven at 150°C. Stir once halfway through.", ifCondition = "chilli=oven"),
            Step("c-sim", "cook", "Slow cook", 21600, "Transfer everything to the slow cooker, cover and cook on low for 6 hours.", ifCondition = "chilli=slow"),
            *riceSteps(listOf("p-rice")).toTypedArray(),
            Step("c-fin", "cook", "Finish and serve", 120, "Stir the {dc:chocolate} into the chilli until melted. Taste, season with salt and serve over the rice with {cr:soured cream} and {co:coriander}.", ifCondition = "add:choc"),
            Step("c-fin", "cook", "Season and serve", 60, "Taste and season with salt. Serve over the rice with {cr:soured cream} and {co:coriander}.", ifCondition = "!add:choc"),
        ),
    )

    fun curry(): Recipe = Recipe(
        id = "curry", title = "Thai green curry", category = "Mains",
        tags = listOf("Spicy", "Quick"), time = 35, difficulty = "Easy", base = 2, icon = "chef-hat",
        summary = "Green curry paste and coconut milk with crisp green beans and jasmine rice.",
        variations = listOf(
            Variation("protein", "swap", "Protein", listOf(Option("chicken", "Chicken"), Option("tofu", "Tofu")), "chicken"),
            Variation("basil", "add", "Thai basil", emptyList(), "", "Aniseed freshness stirred in at the end.", "tb"),
            Variation("bamboo", "add", "Bamboo shoots", emptyList(), "", "Extra crunch and bulk.", "bs"),
        ),
        methods = listOf(riceMethod("Rice", "In a pot")),
        ingredients = listOf(
            Ingredient("cp", "Green curry paste", 3.0, "tbsp", aisle = "World foods"),
            Ingredient("cm", "Coconut milk", 400.0, "ml", note = "1 tin", aisle = "Tins & jars"),
            Ingredient("ck", "Chicken thighs", 300.0, "g", note = "boneless", ifCondition = "protein=chicken", aisle = "Meat"),
            Ingredient("tf", "Firm tofu", 280.0, "g", ifCondition = "protein=tofu", aisle = "Chilled"),
            Ingredient("gb", "Green beans", 150.0, "g", aisle = "Produce"),
            Ingredient("bs", "Bamboo shoots", 120.0, "g", note = "drained", ifCondition = "add:bamboo", aisle = "World foods"),
            Ingredient("fs", "Fish sauce", 1.0, "tbsp", ifCondition = "protein=chicken", aisle = "World foods"),
            Ingredient("ss", "Soy sauce", 1.0, "tbsp", ifCondition = "protein=tofu", aisle = "World foods"),
            Ingredient("li", "Lime", 1.0, "", aisle = "Produce"),
            Ingredient("tb", "Thai basil", 1.0, "handful", "handfuls", ifCondition = "add:basil", aisle = "Produce"),
            Ingredient("ri", "Jasmine rice", 150.0, "g", aisle = "Pantry"),
            Ingredient("wa", "Water", 225.0, "ml", note = "for the rice",
                alt = listOf("rice=microwave" to 300.0)),
        ),
        steps = listOf(
            Step("p-pro", "prep", "Cut the chicken", 300, "Cut the {ck:chicken thighs} into bite-sized pieces.", ifCondition = "protein=chicken"),
            Step("p-pro", "prep", "Press and cube the tofu", 600, "Wrap the {tf:tofu} in kitchen paper, weigh it down for 10 minutes, then cut into cubes.", ifCondition = "protein=tofu"),
            Step("p-beans", "prep", "Trim the beans", 180, "Trim the {gb:green beans} and cut them in half."),
            Step("p-rice", "prep", "Rinse the rice", 120, "Rinse the {ri:jasmine rice} until the water runs clear."),
            *riceSteps(listOf("p-rice")).toTypedArray(),
            Step("c-paste", "cook", "Fry the paste", 120, "Spoon the thick cream from the top of the {cm:coconut milk} into a wok. Fry the {cp:curry paste} in it until fragrant."),
            Step("c-pro", "cook", "Cook the chicken", 300, "Add the {ck:chicken} and stir to coat in the paste. Cook until no longer pink on the outside.", listOf("p-pro"), "protein=chicken"),
            Step("c-pro", "cook", "Brown the tofu", 300, "Add the {tf:tofu} and fry, turning gently, until golden on most sides.", listOf("p-pro"), "protein=tofu"),
            Step("c-sim", "cook", "Simmer", 480, "Pour in the rest of the {cm:coconut milk}, add the {gb:green beans} and simmer until just tender.", listOf("p-beans"), "!add:bamboo"),
            Step("c-sim", "cook", "Simmer", 480, "Pour in the rest of the {cm:coconut milk}, add the {gb:green beans} and {bs:bamboo shoots} and simmer until just tender.", listOf("p-beans"), "add:bamboo"),
            Step("c-fin", "cook", "Season and serve", 60, "Season with the {fs:fish sauce} and a squeeze of {li:lime}. Serve with the rice.", ifCondition = "protein=chicken"),
            Step("c-fin", "cook", "Season and serve", 60, "Season with the {ss:soy sauce} and a squeeze of {li:lime}. Serve with the rice.", ifCondition = "protein=tofu"),
            Step("c-basil", "cook", "Add the basil", 30, "Take the wok off the heat and stir through the {tb:Thai basil}.", ifCondition = "add:basil"),
        ),
    )

    fun tagliatelle(): Recipe = Recipe(
        id = "tagliatelle", title = "Tagliatelle with shallot cream", category = "Pasta",
        tags = listOf("Vegetarian", "Quick"), time = 30, difficulty = "Easy", base = 2, icon = "utensils",
        summary = "A quick weeknight pasta with a silky shallot and parmesan sauce.",
        variations = emptyList(),
        methods = listOf(
            Method("pasta", "Pasta", "Which pasta are you using?", listOf(
                MethodOption("dried", "Dried", "clock", "9 min", "Easy to keep in the cupboard."),
                MethodOption("fresh", "Fresh", "zap", "3 min", "Silkier and much quicker."),
            )),
        ),
        ingredients = listOf(
            Ingredient("t1", "Tagliatelle", 200.0, "g", aisle = "Pantry",
                alt = listOf("pasta=fresh" to 250.0)),
            Ingredient("t2", "Butter", 40.0, "g", note = "unsalted, cold", aisle = "Dairy"),
            Ingredient("t3", "Shallot", 1.0, "", note = "finely chopped", aisle = "Produce"),
            Ingredient("t4", "Double cream", 200.0, "ml", aisle = "Dairy"),
            Ingredient("t5", "Parmesan", 60.0, "g", note = "finely grated", aisle = "Dairy"),
            Ingredient("t6", "Flat-leaf parsley", 1.0, "handful", "handfuls", note = "chopped", aisle = "Produce"),
        ),
        steps = listOf(
            Step("p-sh", "prep", "Chop the shallot", 180, "Peel and finely chop the {t3:shallot}."),
            Step("p-pa", "prep", "Grate and chop", 180, "Finely grate the {t5:parmesan} and chop the {t6:parsley}."),
            Step("c-wat", "cook", "Boil the water", 600, "Bring a large pot of well-salted water to the boil."),
            Step("c-sau", "cook", "Make the sauce", 480, "Melt half the {t2:butter} in a wide pan over a low heat. Cook the {t3:shallot} until soft, then pour in the {t4:cream} and simmer gently.", listOf("p-sh")),
            Step("c-pas", "cook", "Cook the pasta", 540, "Add the {t1:tagliatelle} to the boiling water and cook until just tender. Keep a cup of the cooking water.", ifCondition = "pasta=dried"),
            Step("c-pas", "cook", "Cook the pasta", 180, "Drop the {t1:fresh tagliatelle} into the boiling water and cook for 2–3 minutes. Keep a cup of the cooking water.", ifCondition = "pasta=fresh"),
            Step("c-tog", "cook", "Bring it together", 60, "Toss the pasta into the sauce with the rest of the {t2:butter} and the {t5:parmesan}. Loosen with pasta water and finish with the {t6:parsley}.", listOf("p-pa")),
        ),
    )

    fun shakshuka(): Recipe = Recipe(
        id = "shakshuka", title = "Shakshuka", category = "Breakfast",
        tags = listOf("Vegetarian", "One-pot"), time = 35, difficulty = "Medium", base = 2, icon = "egg",
        summary = "Eggs poached in a smoky pepper and tomato sauce, finished with feta.",
        variations = listOf(
            Variation("harissa", "add", "Harissa", emptyList(), "", "Swaps the paprika smoke for chilli warmth.", "hr"),
        ),
        methods = listOf(
            Method("eggs", "The eggs", "How do you want to cook the eggs?", listOf(
                MethodOption("hob", "Covered on the hob", "flame", "7 min", "Fast, with runny yolks."),
                MethodOption("oven", "In the oven", "layers", "10 min", "Even, set whites at 200°C."),
            )),
        ),
        ingredients = listOf(
            Ingredient("s1", "Onion", 1.0, "", note = "sliced", aisle = "Produce"),
            Ingredient("s2", "Red peppers", 2.0, "", note = "sliced", aisle = "Produce"),
            Ingredient("s3", "Garlic", 2.0, "clove", "cloves", aisle = "Produce"),
            Ingredient("s4", "Chopped tomatoes", 400.0, "g", note = "1 tin", aisle = "Tins & jars"),
            Ingredient("s5", "Smoked paprika", 1.0, "tsp", aisle = "Spices"),
            Ingredient("hr", "Harissa", 1.0, "tbsp", ifCondition = "add:harissa", aisle = "World foods"),
            Ingredient("s6", "Eggs", 4.0, "", aisle = "Dairy & eggs"),
            Ingredient("s7", "Feta", 50.0, "g", aisle = "Dairy & eggs"),
        ),
        steps = listOf(
            Step("p-veg", "prep", "Slice the vegetables", 420, "Thinly slice the {s1:onion} and {s2:peppers}, and crush the {s3:garlic}."),
            Step("c-veg", "cook", "Soften the vegetables", 600, "Cook the {s1:onion} and {s2:peppers} in a little oil until soft. Add the {s3:garlic} and {s5:paprika} for the last minute.", listOf("p-veg")),
            Step("c-sau", "cook", "Simmer the sauce", 600, "Pour in the {s4:tomatoes}, season and simmer until thick.", ifCondition = "!add:harissa"),
            Step("c-sau", "cook", "Simmer the sauce", 600, "Stir in the {hr:harissa}, pour in the {s4:tomatoes}, season and simmer until thick.", ifCondition = "add:harissa"),
            Step("c-egg", "cook", "Cook the eggs", 420, "Make four hollows and crack in the {s6:eggs}. Cover and cook until the whites are set. Crumble over the {s7:feta}.", ifCondition = "eggs=hob"),
            Step("c-egg", "cook", "Bake the eggs", 600, "Make four hollows, crack in the {s6:eggs} and bake at 200°C until the whites are set. Crumble over the {s7:feta}.", ifCondition = "eggs=oven"),
        ),
    )

    fun carrots(): Recipe = Recipe(
        id = "carrots", title = "Roast carrots with tahini", category = "Sides",
        tags = listOf("Vegetarian", "Make-ahead"), time = 45, difficulty = "Easy", base = 4, icon = "carrot",
        summary = "Sweet roasted carrots with cumin, a lemony tahini dressing and a little honey.",
        variations = emptyList(),
        methods = listOf(
            Method("roast", "The carrots", "How do you want to roast them?", listOf(
                MethodOption("oven", "Oven", "flame", "30 min", "At 220°C on a single tray."),
                MethodOption("air", "Air fryer", "zap", "18 min", "At 200°C, shaken halfway."),
            )),
        ),
        ingredients = listOf(
            Ingredient("c1", "Carrots", 800.0, "g", note = "halved lengthways", aisle = "Produce"),
            Ingredient("c2", "Olive oil", 3.0, "tbsp", aisle = "Pantry"),
            Ingredient("c3", "Cumin seeds", 1.0, "tsp", aisle = "Spices"),
            Ingredient("c4", "Tahini", 3.0, "tbsp", aisle = "Tins & jars"),
            Ingredient("c5", "Lemon", 1.0, "", note = "juiced", aisle = "Produce"),
            Ingredient("c6", "Honey", 1.0, "tsp", aisle = "Pantry"),
        ),
        steps = listOf(
            Step("p-car", "prep", "Prepare the carrots", 300, "Halve the {c1:carrots} lengthways and toss with the {c2:olive oil} and {c3:cumin seeds}."),
            Step("p-dre", "prep", "Make the dressing", 180, "Whisk the {c4:tahini} with the {c5:lemon juice}, a pinch of salt and enough cold water to make it pourable."),
            Step("c-roa", "cook", "Roast the carrots", 1800, "Spread the {c1:carrots} on a tray and roast at 220°C until caramelised.", listOf("p-car"), "roast=oven"),
            Step("c-roa", "cook", "Air-fry the carrots", 1080, "Air-fry the {c1:carrots} at 200°C, shaking the basket halfway.", listOf("p-car"), "roast=air"),
            Step("c-ser", "cook", "Dress and serve", 60, "Spoon the dressing over the warm carrots and drizzle with the {c6:honey}.", listOf("p-dre")),
        ),
    )

    /** A rice side (local ids) with pot / microwave / rice cooker, for the Sides tests. */
    fun riceSide(): Side = Side(
        id = "rice", label = "Rice", d = "Soaks up the sauce.",
        ingredients = listOf(
            Ingredient("ri", "Basmati rice", 200.0, "g", aisle = "Pantry"),
            Ingredient("wa", "Water", 400.0, "ml"),
        ),
        methods = listOf(riceMethod()),
        steps = listOf(
            Step("rinse", "prep", "Rinse the rice", 0, "Rinse the {ri:rice} until the water runs clear."),
            Step("r-pot", "cook", "Cook the rice", 1080, "Cook the {ri:rice} with the {wa:water} in a pot.", listOf("rinse"), "rice=pot"),
            Step("r-mw", "cook", "Microwave the rice", 720, "Microwave the {ri:rice} and {wa:water}.", listOf("rinse"), "rice=microwave"),
            Step("r-rc", "cook", "Start the rice cooker", 1800, "Cook the {ri:rice} in the rice cooker.", listOf("rinse"), "rice=cooker"),
        ),
    )

    fun chilliWithRice(): Recipe = chilli().copy(sides = listOf(riceSide()))

    fun all(): List<Recipe> = listOf(chilli(), curry(), tagliatelle(), shakshuka(), carrots())
}
