INSERT INTO cultivo (nombre, nombre_cientifico, frecuencia_riego_dias, humedad_minima, luz_requerida) VALUES
                                                                                                          ('Albahaca',      'Ocimum basilicum',     2,  40, 'Sol directo'),
                                                                                                          ('Lechuga',       'Lactuca sativa',       1,  50, 'Semisombra'),
                                                                                                          ('Tomate cherry', 'Solanum lycopersicum', 1,  45, 'Sol directo'),
                                                                                                          ('Hierbabuena',   'Mentha spicata',       2,  45, 'Semisombra'),
                                                                                                          ('Cilantro',      'Coriandrum sativum',   2,  40, 'Sol directo'),
                                                                                                          ('Fresa',         'Fragaria x ananassa',  2,  45, 'Sol directo'),
                                                                                                          ('Chile',         'Capsicum annuum',      3,  35, 'Sol directo'),
                                                                                                          ('Sabila',        'Aloe vera',            14, 15, 'Sol directo')
    ON CONFLICT (nombre) DO NOTHING;