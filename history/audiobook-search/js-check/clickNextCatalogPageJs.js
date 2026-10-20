
    (function() {
        var elements = document.querySelectorAll('button, [role="button"]');
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            var label = ((element.textContent || '') + ' ' + (element.getAttribute('aria-label') || '')).trim();
            if (element.disabled || element.getAttribute('aria-disabled') === 'true') continue;
            if (/^(?:показать|загрузить|читать)\s+(?:еще|ещё)|следующ|next|load more/i.test(label)) {
                var pagination = element.closest('[class*="pagin"], [class*="pager"], nav');
                if (pagination || /(?:показать|загрузить|load)\s+(?:еще|ещё|more)/i.test(label)) {
                    element.click(); return true;
                }
            }
        }
        return false;
    })();
