INSERT INTO literature_refs (title, authors, source, pub_year, lang, gbt_citation, keywords)
SELECT '深度学习在教育评估中的应用研究', '王明, 李华', '现代教育技术', 2023, 'zh',
       '王明, 李华. 深度学习在教育评估中的应用研究[J]. 现代教育技术, 2023(5): 45-52.',
       '深度学习,教育评估,智能教学'
WHERE NOT EXISTS (
    SELECT 1 FROM literature_refs WHERE title = '深度学习在教育评估中的应用研究'
);

INSERT INTO literature_refs (title, authors, source, pub_year, lang, gbt_citation, keywords)
SELECT '基于大语言模型的学术写作辅助工具综述', '张涛, 陈静', '计算机应用研究', 2024, 'zh',
       '张涛, 陈静. 基于大语言模型的学术写作辅助工具综述[J]. 计算机应用研究, 2024(3): 801-810.',
       '大语言模型,学术写作,人机协同'
WHERE NOT EXISTS (
    SELECT 1 FROM literature_refs WHERE title = '基于大语言模型的学术写作辅助工具综述'
);

INSERT INTO literature_refs (title, authors, source, pub_year, lang, gbt_citation, keywords)
SELECT 'Knowledge Graph Enhanced Academic Writing', 'Brown A, Lee K', 'Journal of AI Research', 2023, 'en',
       'Brown A, Lee K. Knowledge Graph Enhanced Academic Writing[J]. Journal of AI Research, 2023, 28(2): 101-118.',
       'knowledge graph,academic writing'
WHERE NOT EXISTS (
    SELECT 1 FROM literature_refs WHERE title = 'Knowledge Graph Enhanced Academic Writing'
);
