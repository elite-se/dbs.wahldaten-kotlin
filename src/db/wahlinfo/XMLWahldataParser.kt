package db.wahlinfo

import org.jdom2.input.SAXBuilder
import java.io.File
import java.util.logging.Logger

data class Wahlkreis(val name: String, var anzahlSitzplatze: Int?, var schluessel: Int?, var id: Int?)
data class Stimmkreis(var name: String?, val schluessel: Int, val wahlkreis: Wahlkreis, var id: Int?, var wahlberechtigte: Int?,
                      var ungueltigeErststimmen: Int?, var ungueltigeZweitstimmen: Int?)
data class Partei(val name: String, var id: Int?)
data class Kandidat(val vorname: String, val nachname: String, val partei: Partei, val wahlkreis: Wahlkreis,
                    val listenplatz: Int, val gesamtstimmen: Int, val zweitstimmen: Int, var stimmkreis: Stimmkreis?,
                    var erststimmen: Int?, var id: Int?, var direktkandidatID: Int?)

data class WahlData(val wahlkreise: MutableSet<Wahlkreis>, val parteien: MutableSet<Partei>, val kandidaten: MutableSet<Kandidat>,
                    val stimmkreise: MutableSet<Stimmkreis>, val stimmkreisKandidatenStimmen: MutableMap<Pair<Stimmkreis, Kandidat>, Int>,
                    val stimmkreisParteienStimmen: MutableMap<Pair<Stimmkreis, Partei>, Int>)

fun main() {
    val wahlData = XMLWahldataParser().importWahlinfo(
        "2018Ergebnisse_final.xml",
        "2018AllgemeineInformationen.xml", 2018)

    val wahlData2013 = XMLWahldataParser().importWahlinfo(
        "2013Ergebnisse_final.xml",
        "2013AllgemeineInformationen.xml", 2013)

    /*
        TODO do something with the data :)
        Vorsicht: Es gibt im Datenset mehrere Kandidaten, die identische Vor/Nachnamen haben

        Die Wahlbeteiligung lässt sich nicht 100% genau aus den Daten berechnen, weil "nicht abgegebene Stimmen" (d.h.
        Wähler wurde im Wahllokal abhakt, aber die Stimme wurde nicht abgegeben, vermutlich weil Zettel nicht abgegeben
        wurde) nicht enthalten sind. Normalerweise lässt sich die Wahlbeteiligung aber trotzdem bis auf die erste Prozent-
        Nachkommastelle genau berechnen

        Die Daten enthalten schon alle Stimmaggregate, die man so braucht.

        Disclaimer: Wir wissen nicht, ob die Daten zu 100% korrekt sind. Bisher haben wir jedoch noch keinen Fehler
        gefunden. Die Daten wurden ursprünglich von Webseiten geparsed.
    */
    println("Am besten hier debuggen und die Daten anschauen :)")
}

class XMLWahldataParser() {
    private val wahlkreisSet = HashSet<Wahlkreis>()
    private val parteiSet = HashSet<Partei>()
    private val kandidatenSet = HashSet<Kandidat>()
    private val stimmkreisSet = HashSet<Stimmkreis>()
    private val stimmkreisKandidatenStimmen = HashMap<Pair<Stimmkreis, Kandidat>, Int>()
    private val stimmkreisParteienStimmen = HashMap<Pair<Stimmkreis, Partei>, Int>()

    fun importWahlinfo(ergebnisseLocation: String, allgemeineInfoLocation: String, wahljahr: Int): WahlData {
        parseWahlinfoXML(ergebnisseLocation)
        populateStimmkreise(allgemeineInfoLocation, stimmkreisSet)
        populateWahlkreise(wahlkreisSet, wahljahr)

        return WahlData(
            wahlkreisSet,
            parteiSet,
            kandidatenSet,
            stimmkreisSet,
            stimmkreisKandidatenStimmen,
            stimmkreisParteienStimmen
        )
    }

    private fun parseWahlinfoXML(fileLocation: String) {
        val startTime = System.currentTimeMillis()
        val xmlFile = File(fileLocation)
        val saxBuilder = SAXBuilder()
        val document = saxBuilder.build(xmlFile)

        val root = document.rootElement

        val wahlkreisElements = root.children

        for (currWahlkreisElem in wahlkreisElements) {
            val currWahlkreis = Wahlkreis(currWahlkreisElem.getChild("Name").text, null, null, null)
            wahlkreisSet.add(currWahlkreis)

            val parteiElements = currWahlkreisElem.getChildren("Partei")
            for (parteiElement in parteiElements) {
                var currPartei = Partei(parteiElement.getChildText("Name"), null)
                if (!parteiSet.add(currPartei)) {
                    currPartei = parteiSet.toList().first { it == currPartei }
                }

                for (kandidatenElement in parteiElement.getChildren("Kandidat")) {
                    val vorname = kandidatenElement.getChildText("Vorname").trim()
                    val nachname = kandidatenElement.getChildText("Nachname").trim()
                    val listenplatz = kandidatenElement.getChildText("AnfangListenPos").trim().toInt()
                    val gesamtstimmen = kandidatenElement.getChildText("Gesamtstimmen").trim().toInt()
                    val zweitstimmen = kandidatenElement.getChildText("Zweitstimmen").trim().toInt()
                    val currKandidat = Kandidat(
                        vorname, nachname, currPartei, currWahlkreis, listenplatz, gesamtstimmen, zweitstimmen,
                        null, null, null, null
                    )

                    for (stimmkreisElement in kandidatenElement.getChildren("Stimmkreis")) {
                        val stimmkreisNr = stimmkreisElement.getChildText("NrSK").trim().toInt()
                        val kandidatenLoseStimmen = stimmkreisElement.getChildText("ZweitSohneKandidat").trim().toInt()
                        val tempStimmkreis = Stimmkreis(null, stimmkreisNr, currWahlkreis, null, null, null, null)
                        stimmkreisSet.add(tempStimmkreis)
                        val currStimmkreis = stimmkreisSet.toList().first { it == tempStimmkreis }

                        val numStimmenElem = stimmkreisElement.getChild("NumStimmen")
                        if (numStimmenElem.getAttribute("Stimmentyp").value == "Erststimmen") {
                            currKandidat.stimmkreis = currStimmkreis
                            currKandidat.erststimmen = numStimmenElem.text.trim().toInt()
                        } else {
                            stimmkreisKandidatenStimmen[Pair(currStimmkreis, currKandidat)] = numStimmenElem.text.trim().toInt()
                            if (!stimmkreisParteienStimmen.containsKey(Pair(currStimmkreis, currPartei))) {
                                stimmkreisParteienStimmen[Pair(currStimmkreis, currPartei)] = kandidatenLoseStimmen
                            }
                        }
                    }
                    kandidatenSet.add(currKandidat)
                }
            }
        }
        println("Finished XML parsing for ${xmlFile.absolutePath} in ${System.currentTimeMillis() - startTime} ms")
    }

    /**
     * @param fileLocation der ort der xml datei, die zusätzliche infos über stimm- und wahlkreise enthält (z.b. anzahl stimmberechtigte)
     * @param stimmkreise hier werden die daten eingefügt, die aus dem xml geparsed werden, daher mutable
     */
    private fun populateStimmkreise(fileLocation: String, stimmkreise: MutableSet<Stimmkreis>) {
        val startTime = System.currentTimeMillis()
        val xmlFile = File(fileLocation)
        val saxBuilder = SAXBuilder()
        val document = saxBuilder.build(xmlFile)

        val root = document.rootElement

        for (regionaleinheit in root.children) {
            val allgemeineAngaben = regionaleinheit.getChild("Allgemeine_Angaben")
            val schluesselNummer = allgemeineAngaben.getChildTextTrim("Schluesselnummer").toInt()
            // es sollen nur echte stimmkreise berücksichtigt werden, in der xml sind aber ein paar aggregierte "regionaleinheiten"
            if (schluesselNummer >= 900 || schluesselNummer % 100 == 0)
                continue
            val stimmkreisName = allgemeineAngaben.getChildTextTrim("Name_der_Regionaleinheit")
            val stimmkreis = stimmkreise.toList().first { it.schluessel == schluesselNummer }
            stimmkreis.name = stimmkreisName
            stimmkreis.wahlberechtigte = allgemeineAngaben.getChildTextTrim("Stimmberechtigte").toInt()
            stimmkreis.ungueltigeErststimmen = allgemeineAngaben.getChildTextTrim("ungueltige_Erststimmen_der_aktuellen_Wahl").toInt()
            stimmkreis.ungueltigeZweitstimmen = allgemeineAngaben.getChildTextTrim("ungueltige_Zweitstimmen_der_aktuellen_Wahl").toInt()
        }
        println("Finished XML parsing for ${xmlFile.absolutePath} in ${System.currentTimeMillis() - startTime} ms")
    }

    /**
     * Manually add information about wahlkreise - was easier than parsing a file
     */
    private fun populateWahlkreise(wahlkreise: MutableSet<Wahlkreis>, wahljahr: Int) {
        // Quelle: https://www.stmi.bayern.de/assets/stmi/suv/wahlen/stimmkreisbericht-2016-r.pdf
        if (wahljahr == 2018) {
            for (wahlkreis in wahlkreise) {
                when (wahlkreis.name) {
                    "Oberbayern" -> { wahlkreis.anzahlSitzplatze = 61; wahlkreis.schluessel = 901 }
                    "Niederbayern" -> { wahlkreis.anzahlSitzplatze = 18; wahlkreis.schluessel = 902 }
                    "Oberpfalz" -> { wahlkreis.anzahlSitzplatze = 16; wahlkreis.schluessel = 903 }
                    "Oberfranken" -> { wahlkreis.anzahlSitzplatze  = 16; wahlkreis.schluessel = 904 }
                    "Mittelfranken" -> { wahlkreis.anzahlSitzplatze = 24; wahlkreis.schluessel = 905 }
                    "Unterfranken" -> { wahlkreis.anzahlSitzplatze = 19; wahlkreis.schluessel = 906 }
                    "Schwaben" -> { wahlkreis.anzahlSitzplatze = 26; wahlkreis.schluessel = 907 }
                }
            }
        } else if (wahljahr == 2013) {
            for (wahlkreis in wahlkreise) {
                when (wahlkreis.name) {
                    "Oberbayern" -> { wahlkreis.anzahlSitzplatze = 60; wahlkreis.schluessel = 901 }
                    "Niederbayern" -> { wahlkreis.anzahlSitzplatze = 18; wahlkreis.schluessel = 902 }
                    "Oberpfalz" -> { wahlkreis.anzahlSitzplatze = 16; wahlkreis.schluessel = 903 }
                    "Oberfranken" -> { wahlkreis.anzahlSitzplatze  = 16; wahlkreis.schluessel = 904 }
                    "Mittelfranken" -> { wahlkreis.anzahlSitzplatze = 24; wahlkreis.schluessel = 905 }
                    "Unterfranken" -> { wahlkreis.anzahlSitzplatze = 20; wahlkreis.schluessel = 906 }
                    "Schwaben" -> { wahlkreis.anzahlSitzplatze = 26; wahlkreis.schluessel = 907 }
                }
            }
        }
    }
}

