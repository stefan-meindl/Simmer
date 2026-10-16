package com.simmer.app.domain

/**
 * The prose of the Reader prompt in one language (string resources `reader_prompt_*`). The JSON
 * schema and example are not here: their keys are English whatever the language.
 */
data class ReaderPromptTexts(
    val intro: String,
    val rules: String,
    /** Rules for both styles: clearer steps, tips, notes. */
    val enrichment: String,
    val faithful: String,
    val liberty: String,
    val schemaHeading: String,
    val exampleHeading: String,
    /** Kitchen mode Free for all. */
    val kitchenAny: String,
    /** Strict and Medium; `%1$s` is the appliance list. */
    val kitchenOnly: String,
    /** The appliance list when the cook owns none. */
    val kitchenNone: String,
    val kitchenStrict: String,
    val kitchenMedium: String,
    val recipeTextHeading: String,
    /** Said instead of the recipe text when the field is empty (a photo or link goes in the chatbot). */
    val noRecipeText: String,
)

/**
 * The Reader prompt: what the user copies into a chatbot. Instructions, the recipe JSON schema, an
 * example in language [lang], the enrichment rules for [style], the Kitchen line with the appliance rule for [mode] (appliance
 * [applianceNames] already in the prompt language) and the [recipeText] when there is any.
 */
fun readerPrompt(
    texts: ReaderPromptTexts,
    recipeText: String,
    applianceNames: List<String>,
    mode: KitchenMode,
    lang: String,
    style: ImportStyle = ImportStyle.Faithful,
): String {
    val kitchen = if (mode == KitchenMode.Free) {
        texts.kitchenAny
    } else {
        val owned = applianceNames.joinToString(", ").ifEmpty { texts.kitchenNone }
        val rule = if (mode == KitchenMode.Strict) texts.kitchenStrict else texts.kitchenMedium
        texts.kitchenOnly.format(owned) + " " + rule
    }
    val recipe = recipeText.trim().let {
        if (it.isEmpty()) texts.noRecipeText else texts.recipeTextHeading + "\n" + it
    }
    return listOf(
        texts.intro,
        texts.rules,
        texts.enrichment + "\n\n" + if (style == ImportStyle.Liberty) texts.liberty else texts.faithful,
        texts.schemaHeading + "\n" + READER_PROMPT_SCHEMA,
        texts.exampleHeading + "\n" + readerPromptExample(lang),
        kitchen,
        recipe,
    ).joinToString("\n\n")
}

/** The recipe JSON shape the chatbot is asked to fill (what [readChatbotReply] reads). Keys are English in every language. */
const val READER_PROMPT_SCHEMA =
    """{"title":"","summary":"","category":"","tags":[],"time":0,"difficulty":"","base":0,"icon":"",""" +
        """"ingredients":[{"id":"i1","name":"","q":0,"u":"","note":"","aisle":"","tips":[],"if":""}],""" +
        """"variations":[{"id":"v1","kind":"add","label":"","d":"","ing":"i1"}],""" +
        """"methods":[{"id":"m1","label":"","q":"","options":[{"v":"","l":"","icon":"flame","time":"","d":""}]}],""" +
        """"steps":[{"id":"p1","ph":"prep","t":"","b":"","d":0,"needs":[],"if":""}],"notes":[]}"""

/** A small finished recipe in the schema, with its text in [lang] ("de" for German, English otherwise). */
fun readerPromptExample(lang: String): String = if (lang == "de") EXAMPLE_DE else EXAMPLE_EN

private const val EXAMPLE_EN =
    """{"title":"Sheet-pan lemon chicken","summary":"Crisp chicken thighs roasted with potatoes and lemon on one tray.","category":"Mains","tags":["One-pot"],"time":60,"difficulty":"Easy","base":4,"icon":"flame",""" +
        """"ingredients":[""" +
        """{"id":"i1","name":"Chicken thighs","q":8,"u":"","note":"skin on, bone in","aisle":"Meat"},""" +
        """{"id":"i2","name":"Baby potatoes","q":750,"u":"g","note":"halved","aisle":"Produce","tips":["Waxy potatoes hold their shape best."]},""" +
        """{"id":"i3","name":"Garlic","q":6,"u":["clove","cloves"],"aisle":"Produce"},""" +
        """{"id":"i4","name":"Chilli flakes","q":1,"u":["pinch","pinches"],"aisle":"Spices","if":"add:v1"},""" +
        """{"id":"i5","name":"Smoked paprika","q":1,"u":"tsp","aisle":"Spices","if":"add:v2"}],""" +
        """"variations":[{"id":"v1","kind":"add","label":"Chilli flakes","d":"A gentle heat in the rub.","ing":"i4"},""" +
        """{"id":"v2","kind":"suggest","label":"Smoked paprika","d":"Smoky depth.","ing":"i5"}],""" +
        """"methods":[{"id":"m1","label":"The chicken","q":"How do you want to cook it?","options":[""" +
        """{"v":"oven","l":"Oven","icon":"flame","time":"55 min","d":"One tray at 220°C."},""" +
        """{"v":"air","l":"Air fryer","icon":"zap","time":"50 min","d":"Two batches at 200°C."}]}],""" +
        """"steps":[""" +
        """{"id":"p1","ph":"prep","t":"Prepare the tray","b":"Halve the {i2:potatoes} and tip them on a tray with the {i3:garlic}.","d":0,"needs":[]},""" +
        """{"id":"c1","ph":"cook","t":"Roast","b":"Add the {i1:chicken} skin side up and roast at 220°C until the skin is golden and crisp and the juices run clear. Pat the skin dry first for a crisper result.","d":2280,"needs":["p1"],"if":"m1=oven"},""" +
        """{"id":"c1","ph":"cook","t":"Air-fry","b":"Air-fry the {i1:chicken} and potatoes at 200°C in two batches.","d":1500,"needs":["p1"],"if":"m1=air"}],""" +
        """"notes":["Chilli flakes were optional, so they are an add-in.","Added a tip on potatoes and a doneness cue.","Suggested smoked paprika for smoky depth."]}"""

private const val EXAMPLE_DE =
    """{"title":"Blech-Zitronenhähnchen","summary":"Knusprige Hähnchenschenkel mit Kartoffeln und Zitrone auf einem Blech.","category":"Mains","tags":["One-pot"],"time":60,"difficulty":"Easy","base":4,"icon":"flame",""" +
        """"ingredients":[""" +
        """{"id":"i1","name":"Hähnchenschenkel","q":8,"u":"","note":"mit Haut und Knochen","aisle":"Meat"},""" +
        """{"id":"i2","name":"Drillinge","q":750,"u":"g","note":"halbiert","aisle":"Produce","tips":["Festkochende Kartoffeln behalten ihre Form am besten."]},""" +
        """{"id":"i3","name":"Knoblauch","q":6,"u":["Zehe","Zehen"],"aisle":"Produce"},""" +
        """{"id":"i4","name":"Chiliflocken","q":1,"u":["Prise","Prisen"],"aisle":"Spices","if":"add:v1"},""" +
        """{"id":"i5","name":"Geräuchertes Paprikapulver","q":1,"u":"tsp","aisle":"Spices","if":"add:v2"}],""" +
        """"variations":[{"id":"v1","kind":"add","label":"Chiliflocken","d":"Eine leichte Schärfe in der Würzmischung.","ing":"i4"},""" +
        """{"id":"v2","kind":"suggest","label":"Geräuchertes Paprikapulver","d":"Rauchige Tiefe.","ing":"i5"}],""" +
        """"methods":[{"id":"m1","label":"Das Hähnchen","q":"Wie möchtest du es garen?","options":[""" +
        """{"v":"oven","l":"Backofen","icon":"flame","time":"55 Min.","d":"Ein Blech bei 220°C."},""" +
        """{"v":"air","l":"Heißluftfritteuse","icon":"zap","time":"50 Min.","d":"Zwei Durchgänge bei 200°C."}]}],""" +
        """"steps":[""" +
        """{"id":"p1","ph":"prep","t":"Blech vorbereiten","b":"Halbiere die {i2:Drillinge} und gib sie mit dem {i3:Knoblauch} auf ein Blech.","d":0,"needs":[]},""" +
        """{"id":"c1","ph":"cook","t":"Backen","b":"Lege die {i1:Hähnchenschenkel} mit der Haut nach oben darauf und backe sie bei 220°C, bis die Haut goldbraun und knusprig ist und der Saft klar läuft. Trockne die Haut vorher ab, dann wird sie knuspriger.","d":2280,"needs":["p1"],"if":"m1=oven"},""" +
        """{"id":"c1","ph":"cook","t":"Frittieren","b":"Gare die {i1:Hähnchenschenkel} und Kartoffeln bei 200°C in zwei Durchgängen.","d":1500,"needs":["p1"],"if":"m1=air"}],""" +
        """"notes":["Chiliflocken waren optional, daher sind sie ein Extra.","Tipp zu Kartoffeln und Garpunkt-Hinweis ergänzt.","Geräuchertes Paprikapulver für rauchige Tiefe vorgeschlagen."]}"""
