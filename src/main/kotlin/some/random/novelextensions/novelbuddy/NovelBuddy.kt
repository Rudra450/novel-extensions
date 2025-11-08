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
