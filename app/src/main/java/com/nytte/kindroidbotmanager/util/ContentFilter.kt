package com.nytte.kindroidbotmanager.util

/**
 * Content filter that blocks messages violating Kindroid moderation guidelines
 * before they reach the API. Handles common filter-evasion techniques
 * (leet speak, inserted separators, unicode homoglyphs).
 */
object ContentFilter {

    data class FilterResult(val blocked: Boolean, val reason: String = "")

    private val PASSED = FilterResult(false)

    /* ── Leet-speak / homoglyph mapping ─────────────────────────────── */

    private val leetMap = mapOf(
        '0' to 'o', '1' to 'i', '!' to 'i', '|' to 'l',
        '3' to 'e', '4' to 'a', '@' to 'a', '5' to 's',
        '$' to 's', '7' to 't', '+' to 't', '8' to 'b',
        '9' to 'g', '(' to 'c', '<' to 'c',
        // Common unicode homoglyphs
        '\u0430' to 'a', // Cyrillic а
        '\u0435' to 'e', // Cyrillic е
        '\u043E' to 'o', // Cyrillic о
        '\u0440' to 'p', // Cyrillic р
        '\u0441' to 'c', // Cyrillic с
        '\u0443' to 'y', // Cyrillic у
        '\u0445' to 'x', // Cyrillic х
        '\u0456' to 'i', // Cyrillic і
    )

    /* ── Slur / hate-speech terms ───────────────────────────────────── */
    // Source: dsojevic/profanity-list (MIT) — racial, lgbtq, religious
    // tags at severity 3-4, supplemented with LDNOOBW.

    // Slurs that are unambiguous — substring match is fine.
    // These never appear innocently inside normal English words.
    private val slursSubstring = listOf(
        // ── Severity 4 (racial) ──
        "nigger", "sandnigger", "sand nigger", "timbernigger", "timber nigger",
        // ── Severity 3 (racial) ──
        "beaner", "beaners",
        "buddhahead", "buddha head",
        "cameljockey", "camel jockey", "camel jockies",
        "currymuncher", "curry muncher",
        "darkie", "darkies", "darky", "darkey",
        "dunecoon", "dune coon", "dooncoon", "doon coon",
        "jigaboo", "jiggaboo", "jiggerboo",
        "nignog", "nig nog",
        "petrolsniffer", "petrol sniffer",
        "raghead", "rag head", "ragheads", "rag heads",
        "slanteye", "slant eye", "slant-eye",
        "spearchucker",
        "swampguinea", "swamp guinea",
        "towelhead", "towel head",
        "wetback", "wet back",
        "whitepower", "white power",
        "zipperhead", "zipper head",
        // ── Severity 3 (lgbtq) ──
        "analassassin", "anal assassin",
        "analastronaut", "anal astronaut",
        "assbandit", "ass bandit", "arsebandit", "arse bandit",
        "battyboy", "batty boy", "battyboi", "batty boi",
        "beanflicker", "bean flicker",
        "bonesmuggler", "bone smuggler",
        "bootybufer", "booty buffer",
        "brownpiper", "brown piper",
        "brownieking", "brownie king", "browniequeen", "brownie queen",
        "bulldyke",
        "bumchum", "bum chum",
        "bumdriller", "bum driller",
        "buttboy", "butt boy", "bumboy", "bum boy",
        "buttpilot", "butt pilot", "bumpilot", "bum pilot",
        "buttpirate", "butt pirate", "bumpirate", "bum pirate",
        "buttrider", "butt rider", "bumrider", "bum rider",
        "buttrobber", "butt robber", "bumrobber", "bum robber",
        "buttrustler", "butt rustler", "bumrustler", "bum rustler",
        "buttholeengineer", "butthole engineer", "bumholeengineer", "bumhole engineer",
        "carpetmuncher", "carpet muncher",
        "chichimon", "chi chi man",
        "cockpipecosmonaut", "cockpipe cosmonaut",
        "cockstructionworker", "cockstruction worker",
        "craftybutcher", "crafty butcher",
        "cuntboy", "cunt boy",
        "dickgirl", "dick girl",
        "donutmuncher", "donut muncher",
        "donutpuncher", "donut puncher",
        "fagbomb", "fag bomb",
        "faggot", "fagot", "faget",
        "finocchio", "finochio", "finoccio",
        "fudgepacker", "fudge packer",
        "futanari",
        "kittypuncher", "kitty puncher",
        "ladyboy", "lady boy",
        "meatmasseuse", "meat masseuse",
        "muffdiver", "muff diver",
        "musclemary", "muscle mary",
        "oklahomo",
        "peterpuffer", "peter puffer",
        "pisspig", "piss pig",
        "pussypuncher", "pussy puncher",
        "ringraider", "ring raider",
        "shemale", "she male", "she-male",
        // ── Severity 3 (religious) ──
        // (raghead/towelhead already listed above under racial)
    )

    // Slurs that could appear inside innocent words — use word-boundary regex.
    // "coon" in "raccoon", "spic" in "spice", "kike" in "biker", etc.
    private val slursWordBound = listOf(
        // racial
        "\\bcoon\\b", "\\bcoons\\b",
        "\\bchink\\b", "\\bchinks\\b", "\\bchinky\\b",
        "\\bgook\\b", "\\bgooks\\b", "\\bgooky\\b", "\\bgookie\\b",
        "\\bkike\\b", "\\bkikes\\b",
        "\\bpaki\\b", "\\bpakis\\b",
        "\\bpikey\\b", "\\bpikeys\\b",
        "\\bspic\\b", "\\bspics\\b", "\\bspick\\b", "\\bspicks\\b",
        "\\bnegro\\b", "\\bnegroes\\b",
        // lgbtq
        "\\bbufter\\b", "\\bbufty\\b",
        "\\bcishet\\b",
        "\\bcissy\\b", "\\bcissie\\b",
        "\\bdyke\\b", "\\bdykes\\b",
        "\\benby\\b",
        "\\bfag\\b", "\\bfags\\b",
        "\\bgaysian\\b",
        "\\bhermie\\b",
        "\\blesbo\\b", "\\blesbos\\b",
        "\\bleso\\b",
        "\\blezzie\\b", "\\blezzies\\b",
        "\\bpansy\\b",
        "\\bpoof\\b", "\\bpoofs\\b", "\\bpoofy\\b",
        "\\bsissy\\b",
        "\\btgirl\\b",
        "\\btranny\\b", "\\btrannie\\b", "\\btrannies\\b",
        "\\btransbian\\b",
        "\\btwink\\b", "\\btwinks\\b",
        // general hate
        "\\bretard\\b", "\\bretards\\b", "\\bretarded\\b",
    ).map { Regex(it) }

    // Multi-word phrases that should be matched as substrings (lgbtq slurs cont.)
    private val slursPhrases = listOf(
        "bean queen",
        "chicken queen",
        "grey queen", "gray queen",
        "gym bunny",
        "light in the fedora",
        "light in the loafers",
        "light in the pants",
        "limp wristed", "limp-wristed",
        "potato queen",
        "rice queen",
        "switch hitter",
        "cheese eating surrender monkey", "cheese-eating surrender monkey",
    )

    /* ── Minor-related terms — outright blocked ─────────────────────── */

    // Any mention of minors is blocked unconditionally.
    // Word-boundary versions to avoid false positives (e.g. "kidney", "kiddingly")
    private val minorTermsWordBound = listOf(
        "\\bchild\\b", "\\bchildren\\b",
        "\\bkid\\b", "\\bkids\\b",
        "\\bbaby\\b", "\\bbabies\\b",
        "\\bminor\\b", "\\bminors\\b",
        "\\btoddler\\b", "\\btoddlers\\b",
        "\\binfant\\b", "\\binfants\\b",
        "\\bnewborn\\b", "\\bnewborns\\b",
    ).map { Regex(it) }

    // These are unambiguous — substring match is safe
    private val minorTermsSubstring = listOf(
        "underage", "under age", "underaged",
        "preteen", "pre teen", "preteens",
        "loli", "lolita", "shota",
        "young boy", "young girl",
        "little boy", "little girl",
        "little sister", "little brother",
        "yr old", "year old", "years old",
        "yo girl", "yo boy",
        "pthc", "pedophil", "paedophil",
        "pedobear",
    )

    /* ── CSAM-related terms (legacy combo check kept as fallback) ──── */

    private val minorTerms = listOf(
        "child", "children", "kid", "kids",
        "underage", "under age", "underaged",
        "minor", "minors",
        "preteen", "pre teen",
        "loli", "lolita", "shota",
        "toddler", "infant", "baby",
        "young boy", "young girl",
        "little boy", "little girl",
        "little sister", "little brother",
        "yr old", "year old", "years old",
        "yo girl", "yo boy",
    )

    private val sexualTerms = listOf(
        "sex", "sexual", "sexually",
        "fuck", "fucked", "fucking",
        "rape", "raped", "raping",
        "molest", "molested", "molesting",
        "naked", "nude", "nudes",
        "penis", "vagina", "cock", "dick", "pussy",
        "blowjob", "blow job",
        "handjob", "hand job",
        "masturbat",
        "orgasm",
        "cum ", "cumming", "cumshot",
        "erotic",
        "hentai",
        "porn",
        "nsfw",
        "lewd",
        "strip", "stripping",
        "grope", "groping",
        "fondle", "fondling",
        "penetrat",
    )

    /* ── Violence / harm-to-others planning ─────────────────────────── */

    private val violenceTargetTerms = listOf(
        "kill", "murder", "assassinate",
        "bomb", "bombing",
        "shoot", "shooting",
        "stab", "stabbing",
        "attack", "attacking",
        "kidnap", "abduct",
        "poison", "poisoning",
        "strangle", "strangling",
        "massacre", "slaughter",
        "execute", "behead",
        "terroris",
    )

    private val planningTerms = listOf(
        "plan to", "planning to", "going to",
        "will ", "want to", "how to",
        "gonna", "intend to", "preparing to",
        "help me", "tell me how", "show me how",
        "step by step", "instructions",
        "tutorial",
        "recipe for", "guide to",
    )

    private val harmTargets = listOf(
        "my wife", "my husband", "my boss",
        "my neighbor", "my neighbour",
        "my teacher", "my ex",
        "my coworker", "my colleague",
        "my family", "my parent",
        "school", "mosque", "church", "synagogue", "temple",
        "crowd", "public", "government",
    )

    /* ── Self-harm planning ─────────────────────────────────────────── */

    private val selfHarmMethods = listOf(
        "sleeping pills", "overdose", "overdosing",
        "slit my wrist", "cut my wrist", "slit my throat",
        "hang myself", "hanging myself",
        "shoot myself",
        "jump off", "jump from",
        "drown myself",
        "poison myself",
        "end my life", "end it all",
        "kill myself", "killing myself",
    )

    private val selfHarmPlanning = listOf(
        "plan to", "planning to", "going to",
        "will ", "tonight", "tomorrow",
        "this friday", "this weekend",
        "saved up", "bought",
        "written my note", "suicide note",
        "goodbye letter", "final decision",
        "decided to", "made up my mind",
    )

    /* ── Doxing / stalking ──────────────────────────────────────────── */

    // Multi-word phrases safe for substring matching
    private val doxPhrase = listOf(
        "doxx", "doxing", "doxxing",
        "swatting",
        "home address", "ip address",
        "phone number", "social security",
        "find where", "where they live", "where he lives", "where she lives",
    )

    // Short words needing word-boundary
    private val doxWordBound = listOf(
        "\\bdox\\b",
        "\\bswat\\b",
    ).map { Regex(it) }

    /* ── Illegal content requests ───────────────────────────────────── */

    private val illegalRequestTerms = listOf(
        "make a bomb", "build a bomb", "pipe bomb",
        "make meth", "cook meth",
        "ricin", "sarin", "anthrax",
        "biological weapon", "chemical weapon",
        "dirty bomb",
        "how to hack", "hack into",
    )

    /* ── Normalisation ──────────────────────────────────────────────── */

    /**
     * Normalise text to defeat common filter-evasion tricks:
     *  1. Lowercase
     *  2. Map leet-speak and homoglyphs to ASCII
     *  3. Strip separators inserted between letters (n.i.g → nig)
     *  4. Collapse runs of the same letter (nigggg → nigg)
     */
    private fun normalize(text: String): String {
        // Step 1 + 2: lowercase and map leet chars
        val mapped = text.lowercase().map { leetMap[it] ?: it }

        // Step 3: remove non-letter-non-space chars sitting between letters
        val stripped = buildString {
            for (ch in mapped) {
                if (ch.isLetter() || ch.isWhitespace() || ch.isDigit()) {
                    append(ch)
                }
            }
        }

        // Step 4: collapse repeated chars (niiigggger → nigger, keep max 2)
        return buildString {
            var prev: Char? = null
            var count = 0
            for (ch in stripped) {
                if (ch == prev) {
                    count++
                    if (count <= 1) append(ch)
                } else {
                    append(ch)
                    prev = ch
                    count = 0
                }
            }
        }
    }

    /**
     * Version with all whitespace removed to catch "n i g g e r" style evasion.
     */
    private fun normalizeCompact(text: String): String =
        normalize(text).replace("\\s+".toRegex(), "")

    /* ── Checking logic ─────────────────────────────────────────────── */

    fun check(message: String): FilterResult {
        val norm = normalize(message)
        val compact = normalizeCompact(message)

        // 0. Outright block any mention of minors
        for (re in minorTermsWordBound) {
            if (re.containsMatchIn(norm)) {
                return FilterResult(true, "Message blocked: any mention of minors is prohibited.")
            }
        }
        for (term in minorTermsSubstring) {
            if (norm.contains(term) || compact.contains(term)) {
                return FilterResult(true, "Message blocked: any mention of minors is prohibited.")
            }
        }

        // 1a. Slurs — unambiguous substring match
        for (slur in slursSubstring) {
            if (compact.contains(slur) || norm.contains(slur)) {
                return FilterResult(true, "Message blocked: contains a slur or hate speech.")
            }
        }
        // 1b. Slurs — word-boundary regex (avoids "raccoon", "spice", etc.)
        for (re in slursWordBound) {
            if (re.containsMatchIn(norm) || re.containsMatchIn(compact)) {
                return FilterResult(true, "Message blocked: contains a slur or hate speech.")
            }
        }
        // 1c. Slurs — multi-word phrases
        for (phrase in slursPhrases) {
            if (norm.contains(phrase)) {
                return FilterResult(true, "Message blocked: contains a slur or hate speech.")
            }
        }

        // 2. CSAM — minor term + sexual term in same message
        val hasMinorRef = minorTerms.any { norm.contains(it) || compact.contains(it) }
        if (hasMinorRef) {
            val hasSexualRef = sexualTerms.any { norm.contains(it) || compact.contains(it) }
            if (hasSexualRef) {
                return FilterResult(true, "Message blocked: sexualised content involving minors is strictly prohibited.")
            }
        }

        // 3. Harm to others — violence term + planning term + real-world target
        val hasViolence = violenceTargetTerms.any { norm.contains(it) }
        if (hasViolence) {
            val hasPlanning = planningTerms.any { norm.contains(it) }
            val hasTarget = harmTargets.any { norm.contains(it) }
            if (hasPlanning && hasTarget) {
                return FilterResult(true, "Message blocked: planning or inciting real-world violence is prohibited.")
            }
        }

        // 4. Self-harm with planning specifics
        val hasSelfHarmMethod = selfHarmMethods.any { norm.contains(it) }
        if (hasSelfHarmMethod) {
            val hasSelfHarmPlan = selfHarmPlanning.any { norm.contains(it) }
            if (hasSelfHarmPlan) {
                return FilterResult(true, "Message blocked: imminent self-harm planning detected. If you are in crisis, please contact a crisis helpline.")
            }
        }

        // 5a. Doxing / stalking — phrase match
        for (term in doxPhrase) {
            if (norm.contains(term) || compact.contains(term)) {
                return FilterResult(true, "Message blocked: doxing, stalking, or harassment is prohibited.")
            }
        }
        // 5b. Doxing — short words with boundary
        for (re in doxWordBound) {
            if (re.containsMatchIn(norm)) {
                return FilterResult(true, "Message blocked: doxing, stalking, or harassment is prohibited.")
            }
        }

        // 6. Illegal content requests (weapons / drugs synthesis)
        for (term in illegalRequestTerms) {
            if (norm.contains(term) || compact.contains(term)) {
                return FilterResult(true, "Message blocked: request for illegal content is prohibited.")
            }
        }

        return PASSED
    }
}
