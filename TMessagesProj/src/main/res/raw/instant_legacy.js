// ES5 reader for Android 5 WebView. The document stays detached and publisher scripts never run.
(function () {
    var request = new XMLHttpRequest();
    request.open('GET', '/index.html', true);
    request.onload = function () {
        try {
            var doc = new DOMParser().parseFromString(request.responseText, 'text/html');
            var body = doc.querySelector('article') || doc.querySelector('main') || doc.body;
            var remaining = 20000;
            function parse(parent, depth) {
                if (depth > 128) throw new Error('Depth');
                var result = [];
                for (var i = 0; i < parent.childNodes.length; i++) {
                    if (--remaining < 0) throw new Error('Size');
                    var node = parent.childNodes[i];
                    if (node.nodeType === 3) {
                        if (node.textContent.trim()) result.push(node.textContent);
                        continue;
                    }
                    if (node.nodeType !== 1) continue;
                    var tag = node.tagName.toLowerCase();
                    if (/^(script|style|noscript|nav|footer|form|button|input|object|embed|link|meta|svg)$/.test(tag)) continue;
                    var value = {tag: tag, content: parse(node, depth + 1)};
                    var attrs = ['src', 'href', 'title', 'alt', 'width', 'height', 'colspan', 'rowspan'];
                    for (var j = 0; j < attrs.length; j++) {
                        var name = attrs[j], text = node.getAttribute(name);
                        if (text && (!/^(src|href)$/.test(name) || (!/^([a-z][a-z0-9+.-]*):/i.test(text.replace(/[\u0000-\u0020]/g, '')) || /^https?:/i.test(text.replace(/[\u0000-\u0020]/g, ''))))) value[name] = text;
                    }
                    result.push(value);
                }
                return result;
            }
            var site = doc.querySelector('meta[property="og:site_name"]');
            window.Instant.done(JSON.stringify({title: doc.title || '', siteName: site ? site.content : '', content: parse(body, 0)}));
        } catch (e) { window.Instant.done('null'); }
    };
    request.onerror = function () { window.Instant.done('null'); };
    request.send();
})();
