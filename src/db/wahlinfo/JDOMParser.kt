package db.wahlinfo

import org.jdom2.input.SAXBuilder
import java.io.File

data class Wahlkreis(val name: String, var anzahlSitzplatze: Int?, var schluessel: Int?)
data class Stimmkreis(var name: String?, val schluessel: Int, val wahlkreis: Wahlkreis)
data class Partei(val name: String)
data class Kandidat(val vorname: String, val nachname: String, val partei: Partei, val wahlkreis: Wahlkreis,
                    val listenplatz: Int, val gesamtstimmen: Int, val zweitstimmen: Int, var stimmkreis: Stimmkreis?,
                    var erststimmen: Int?)

data class WahlData(val wahlkreise: MutableSet<Wahlkreis>, val parteien: MutableSet<Partei>, val kandidaten: MutableSet<Kandidat>,
                    val stimmkreise: MutableSet<Stimmkreis>, val stimmkreisKandidatenStimmen: MutableMap<Pair<Stimmkreis, Kandidat>, Int>,
                    val stimmkreisParteienStimmen: MutableMap<Pair<Stimmkreis, Partei>, Int>)

fun main() {
    JDOMParser().importWahlinfo("2018Ergebnisse_final.xml", "2018AllgemeineInformationen.xml")
}

class JDOMParser() {
    private val wahlkreisSet = HashSet<Wahlkreis>()
    private val parteiSet = HashSet<Partei>()
    private val kandidatenSet = HashSet<Kandidat>()
    private val stimmkreisSet = HashSet<Stimmkreis>()
    private val stimmkreisKandidatenStimmen = HashMap<Pair<Stimmkreis, Kandidat>, Int>()
    private val stimmkreisParteienStimmen = HashMap<Pair<Stimmkreis, Partei>, Int>()

    fun importWahlinfo(ergebnisseLocation: String, allgemeineInfoLocation: String): WahlData {
        parseWahlinfoXML(ergebnisseLocation)
        populateStimmkreise(allgemeineInfoLocation, stimmkreisSet)
        populateWahlkreise(wahlkreisSet)

        return WahlData(wahlkreisSet, parteiSet, kandidatenSet, stimmkreisSet, stimmkreisKandidatenStimmen, stimmkreisParteienStimmen)
    }

    private fun parseWahlinfoXML(fileLocation: String) {
        val startTime = System.currentTimeMillis()
        val xmlFile = File(fileLocation)
        val saxBuilder = SAXBuilder()
        val document = saxBuilder.build(xmlFile)

        val root = document.rootElement

        val wahlkreisElements = root.children

        for (currWahlkreisElem in wahlkreisElements) {
            val currWahlkreis = Wahlkreis(currWahlkreisElem.getChild("Name").text, null, null)
            wahlkreisSet.add(currWahlkreis)

            val parteiElements = currWahlkreisElem.getChildren("Partei");
            for (parteiElement in parteiElements) {
                var currPartei = Partei(parteiElement.getChildText("Name"))
                if (!parteiSet.add(currPartei)) {
                    currPartei = parteiSet.toList().first { it == currPartei }
                }

                for (kandidatenElement in parteiElement.getChildren("Kandidat")) {
                    val vorname = kandidatenElement.getChildText("Vorname").trim()
                    val nachname = kandidatenElement.getChildText("Nachname").trim()
                    val listenplatz = kandidatenElement.getChildText("AnfangListenPos").trim().toInt()
                    val gesamtstimmen = kandidatenElement.getChildText("Gesamtstimmen").trim().toInt()
                    val zweitstimmen = kandidatenElement.getChildText("Zweitstimmen").trim().toInt()
                    val currKandidat = Kandidat(vorname, nachname, currPartei, currWahlkreis, listenplatz, gesamtstimmen, zweitstimmen, null, null)

                    for (stimmkreisElement in kandidatenElement.getChildren("Stimmkreis")) {
                        val stimmkreisNr = stimmkreisElement.getChildText("NrSK").trim().toInt()
                        val kandidatenLoseStimmen = stimmkreisElement.getChildText("ZweitSohneKandidat").trim().toInt()
                        val tempStimmkreis = Stimmkreis(null, stimmkreisNr, currWahlkreis);
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

    private fun populateStimmkreise(fileLocation: String, stimmkreise: MutableSet<Stimmkreis>) {
        val startTime = System.currentTimeMillis()
        val xmlFile = File(fileLocation)
        val saxBuilder = SAXBuilder()
        val document = saxBuilder.build(xmlFile)

        val root = document.rootElement

        for (regionaleinheit in root.children) {
            val allgemeineAngaben = regionaleinheit.getChild("Allgemeine_Angaben")
            val schluesselNummer = allgemeineAngaben.getChildTextTrim("Schluesselnummer").toInt()
            if (schluesselNummer >= 900 || schluesselNummer % 100 == 0)
                continue
            val stimmkreisName = allgemeineAngaben.getChildTextTrim("Name_der_Regionaleinheit")
            stimmkreise.toList().first { it.schluessel == schluesselNummer }.name = stimmkreisName
        }
        println("Finished XML parsing for ${xmlFile.absolutePath} in ${System.currentTimeMillis() - startTime} ms")
    }

    private fun populateWahlkreise(wahlkreise: MutableSet<Wahlkreis>) {
        for (wahlkreis in wahlkreise) {
            when (wahlkreis.name) {
                "Oberbayern" -> { wahlkreis.anzahlSitzplatze = 69; wahlkreis.schluessel = 901 }
                "Niederbayern" -> { wahlkreis.anzahlSitzplatze = 21; wahlkreis.schluessel = 902 }
                "Oberpfalz" -> { wahlkreis.anzahlSitzplatze = 18; wahlkreis.schluessel = 903 }
                "Oberfranken" -> { wahlkreis.anzahlSitzplatze  = 18; wahlkreis.schluessel = 904 }
                "Mittelfranken" -> { wahlkreis.anzahlSitzplatze = 29; wahlkreis.schluessel = 905 }
                "Unterfranken" -> { wahlkreis.anzahlSitzplatze = 19; wahlkreis.schluessel = 906 }
                "Schwaben" -> { wahlkreis.anzahlSitzplatze = 31; wahlkreis.schluessel = 907 }
            }
        }
    }
}

