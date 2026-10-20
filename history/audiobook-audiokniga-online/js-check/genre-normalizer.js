(function(value) {
        value=String(value||'');if(value.length>512)return '';
        return value.toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанры?|категори[яи]|раздел)\s*[:–—-]\s*/i,'')
            .replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*$/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*$/,'')
            .replace(/\s+\d[\d\s.,]*\s*$/,'')
            .replace(/^(?:аудиокниги|книги)\s+(?:в\s+жанре\s+)?/i,'').replace(/\s+аудиокниги$/i,'')
            .replace(/[«»„“”"'’]/g,'').replace(/[–—−.,;:/|-]/g,' ').replace(/\s+/g,' ').trim();
    })