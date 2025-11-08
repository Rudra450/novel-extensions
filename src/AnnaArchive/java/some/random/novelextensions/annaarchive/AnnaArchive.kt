package some.random.novelextensions.novelbuddy

import ani.dantotsu.parsers.Book
import ani.dantotsu.parsers.NovelInterface
import ani.dantotsu.parsers.ShowResponse
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Suppress("unused")
class NovelBuddy : NovelInterface {

    val name = "NovelBuddy"
    val saveName = "novelbuddy"
    val hostUrl = "https://novelbuddy.com"
    val defaultImage = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"

    private fun parseShowResponse(it: Element?): ShowResponse? {
        it ?: return null
        val a = it.selectFirst("a") ?: return null
        val title = a.attr("title").ifEmpty { a.text() }
        val link = a.attr("href").let { href -> if (href.startsWith("http")) href else "$hostUrl$href" }
        var img = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: defaultImage
        val author = it.selectFirst(".author")?.text() ?: "Unknown"
        val extra = mapOf(
            "author" to author,
        )
        return ShowResponse(title, link, img, extra = extra)
    }

    override suspend fun search(query: String, client: Requests): List<ShowResponse> {
        val q = query.replace(" ", "+")
        val response = client.get("$hostUrl/search?q=$q")
        val document = response.document
        val results = document.select("div.book-item")
        return results.mapNotNull { div -> parseShowResponse(div) }
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?, client: Requests): Book {
        val response = client.get(link)
        val document = response.document
        val title = document.selectFirst("h1")?.text() ?: "Untitled"
        var img = document.selectFirst(".book-cover img")?.attr("src") ?: defaultImage
        val description = document.selectFirst(".summary__content")?.text()
        val chapterElements = document.select("ul.main li a")
        val chapterLinks = chapterElements.mapNotNull { a ->
            val href = a.attr("href")
            if (href.startsWith("http")) href else "$hostUrl$href"
        }
        return Book(title, img, description, chapterLinks)
    }
}

fun logger(msg: String) {
    println(msg)
}
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?, client: Requests): Book {
        return client.get(link).document.selectFirst("main")!!.let {
            val name = it.selectFirst("div.text-3xl")!!.text().substringBefore("\uD83D\uDD0D")
            var img = it.selectFirst("img")?.attr("src") ?: ""
            if(img=="") img = defaultImage
            val description = it.selectFirst("div.js-md5-top-box-description")?.text()
            val links = it.select("a.js-download-link")
                .filter { element ->
                    !element.text().contains("Fast") &&
                            !element.attr("href").contains("onion") &&
                            !element.attr("href").contains("/datasets") &&
                            !element.attr("href").contains("1lib") &&
                            !element.attr("href").contains("slow_download")
                }.reversed() //libgen urls are faster
                .flatMap { a ->
                    LinkExtractor(a.attr("href"), client).extractLink() ?: emptyList()
                }
            //logger("Novel search: $name, $img, $description, $links")
            Book(name, img, description, links)
        }
    }
    class LinkExtractor(private val url: String, private val client: Requests) {
        suspend fun extractLink(): List<String>? {
            return when {
                isLibgenUrl(url) || isLibraryLolUrl(url) -> LibgenExtractor(url)
                isSlowDownload(url) -> {
                    try {
                        val response = client.get("https://annas-archive.org$url")
                        val links = response.document.select("a")?.mapNotNull { it.attr("href") }
                        //logger("Novel search extr3: $links")
                        links?.takeWhile { !it.contains("localhost") }
                    } catch (e: Exception) {
                        //logger("Error in isSlowDownload: ${e.message}")
                        null // or handle the exception as needed
                    }
                }

                else -> listOf(url)
            }
        }

        private fun isLibgenUrl(url: String): Boolean {
            val a = url.contains("libgen")
            //logger("Novel search isLibgenUrl: $url, $a")
            return a
        }

        private fun isLibraryLolUrl(url: String): Boolean {
            val a = url.contains("library.lol")
            //logger("Novel search isLibraryLolUrl: $url, $a")
            return a
        }

        private fun isSlowDownload(url: String): Boolean {
            val a = url.contains("slow_download")
            //logger("Novel search isSlowDownload: $url, $a")
            return a
        }

        private suspend fun LibgenExtractor(url: String): List<String>? {
            return try {
                when {
                    url.contains("ads.php") -> {
                        val response = client.get(url)
                        val links = response.document.select("table#main").first()?.getElementsByAttribute("href")?.first()?.attr("href")
                        //logger("Novel search extr: $links")
                        //if substring starts with /ads.php then add the url before it
                        if (links?.startsWith("/ads.php") == true || links?.startsWith("get.php") == true) listOf(url.substringBefore("ads.php") + links)
                        else listOf(links ?: "")
                    }
                    else -> {
                        val response = client.get(url)
                        val links = response.document.selectFirst("div#download")?.select("a")?.mapNotNull { it.attr("href") }
                        //logger("Novel search extr2: $links")
                        links?.takeWhile { !it.contains("localhost") }
                    }
                }
            } catch (e: Exception) {
                //logger("Error during Libgen extraction: ${e.message}")
                null // or handle the exception as needed
            }
        }


    }

    fun List<ShowResponse>.sortByVolume(query:String) : List<ShowResponse> {
        val sorted = groupBy { res ->
            val match = volumeRegex.find(res.name)?.groupValues
                ?.firstOrNull { it.isNotEmpty() }
                ?.substringAfter(" ")
                ?.toDoubleOrNull() ?: Double.MAX_VALUE
            match
        }.toSortedMap().values

        val volumes = sorted.map { showList ->
            val nonDefaultCoverShows = showList.filter { it.coverUrl.url != defaultImage }
            val bestShow = nonDefaultCoverShows.firstOrNull { it.name.contains(query) }
                ?: nonDefaultCoverShows.firstOrNull()
                ?: showList.first()
            bestShow
        }
        val remainingShows = sorted.flatten() - volumes.toSet()

        return volumes + remainingShows
    }

}

fun logger(msg: String) {
    println(msg)
}


