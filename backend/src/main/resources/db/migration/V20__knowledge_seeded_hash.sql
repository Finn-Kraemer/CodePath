-- MD5 des mitgelieferten Textes, mit dem ein Artikel zuletzt angelegt oder aktualisiert wurde. Stimmt der
-- Text noch damit überein, hat ihn niemand im Browser geändert, und der Start darf eine neuere Fassung
-- aus content/wissen/ übernehmen.
ALTER TABLE knowledge_articles ADD COLUMN seeded_hash VARCHAR(32);

-- Bereits angelegte Artikel, die noch genau dem Text aus V17 entsprechen
UPDATE knowledge_articles SET seeded_hash = md5(body)
WHERE (slug, md5(body)) IN (
    ('algorithms', 'ec0cdcf4b3188fecd64f53c98dbe87ef'),
    ('cloud-netze', 'c0d3418accc080a8b806647896c03d7b'),
    ('datenbanken', '71293d89fb6e03643fd6eb1ca3fec315'),
    ('digitaltechnik', 'b831b65f30780e0fbf7e6a455a0ef9bd'),
    ('git', 'fd190879425ba26f85a1cebf387f8a62'),
    ('it-sicherheit', 'd606ced869a3be226e535705dbe0c93f'),
    ('ki-informatik', '1bb5236fa457665287cc1cfcb6c7352c'),
    ('linux', '6c917a896b3b46f07bb8a82065603508'),
    ('programmierung', '67857cb1640ecf1bf472556509236c61'),
    ('web-entwicklung', '1f7d802e1048e09c4e1924ca9f3df405')
);
