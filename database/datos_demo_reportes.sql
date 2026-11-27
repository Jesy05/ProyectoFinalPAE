-- Datos ficticios para probar reportes en los tres meses completos anteriores.
-- Ejecutar solo en plantbee_demo_reportes, después de que Hibernate cree las tablas.
BEGIN;

DO $demo$
DECLARE
    cultivo_demo record;
    mes date;
    usuario_demo bigint;
    colmena_demo bigint;
    modulo_demo bigint;
    cultivo_id_demo bigint;
    planta_demo bigint;
    apodo_demo text;
BEGIN
    IF current_database() <> 'plantbee_demo_reportes' THEN
        RAISE EXCEPTION 'Ejecute este script únicamente en la base plantbee_demo_reportes.';
    END IF;
    PERFORM pg_advisory_xact_lock(98246, 1);

    INSERT INTO usuario (nombre, correo, fecha_registro)
    VALUES ('Equipo Demo Reportes', 'plantbee-demo-reportes@example.test',
            (date_trunc('month', CURRENT_DATE) - interval '4 months')::date)
    ON CONFLICT (correo) DO NOTHING;

    SELECT id INTO usuario_demo FROM usuario WHERE correo = 'plantbee-demo-reportes@example.test';

    SELECT id INTO colmena_demo FROM colmena
    WHERE usuario_id = usuario_demo AND nombre = 'Colmena Demo Reportes';
    IF colmena_demo IS NULL THEN
        INSERT INTO colmena (nombre, ubicacion, fecha_instalacion, usuario_id)
        VALUES ('Colmena Demo Reportes', 'Huerto de demostración',
                (date_trunc('month', CURRENT_DATE) - interval '4 months')::date, usuario_demo)
        RETURNING id INTO colmena_demo;
    END IF;

    INSERT INTO modulo (colmena_id, numero_nivel, capacidad)
    VALUES (colmena_demo, 1, 100)
    ON CONFLICT (colmena_id, numero_nivel) DO NOTHING;

    SELECT id INTO modulo_demo FROM modulo WHERE colmena_id = colmena_demo AND numero_nivel = 1;

    -- Los porcentajes y frecuencias son valores ficticios para la prueba.
    FOR cultivo_demo IN
        SELECT * FROM (VALUES
            (3, 'Rábano', 'Raphanus sativus', 2, 45, 'Agua de lluvia'),
            (3, 'Acelga', 'Beta vulgaris', 3, 50, 'Agua potable'),
            (2, 'Espinaca', 'Spinacia oleracea', 2, 55, 'Agua gris filtrada (biofiltro)'),
            (2, 'Cebollín', 'Allium fistulosum', 3, 40, 'Agua de lluvia'),
            (1, 'Rúcula', 'Eruca vesicaria', 2, 50, 'Agua potable'),
            (1, 'Romero', 'Salvia rosmarinus', 5, 30, 'Agua gris filtrada (biofiltro)')
        ) AS datos(meses_atras, nombre, cientifico, frecuencia, minima, origen)
    LOOP
        mes := (date_trunc('month', CURRENT_DATE) - make_interval(months => cultivo_demo.meses_atras))::date;
        apodo_demo := format('Demo %s %s', to_char(mes, 'YYYY-MM'), cultivo_demo.nombre);

        INSERT INTO cultivo (nombre, nombre_cientifico, frecuencia_riego_dias, humedad_minima)
        VALUES (cultivo_demo.nombre, cultivo_demo.cientifico, cultivo_demo.frecuencia, cultivo_demo.minima)
        ON CONFLICT (nombre) DO NOTHING;
        SELECT id INTO cultivo_id_demo FROM cultivo WHERE nombre = cultivo_demo.nombre;

        SELECT id INTO planta_demo FROM planta WHERE modulo_id = modulo_demo AND apodo = apodo_demo;
        IF planta_demo IS NOT NULL THEN CONTINUE; END IF;

        INSERT INTO planta (apodo, fecha_siembra, estado, cultivo_id, modulo_id)
        VALUES (apodo_demo, mes, 'Creciendo', cultivo_id_demo, modulo_demo)
        RETURNING id INTO planta_demo;

        INSERT INTO sensor (codigo, humedad_actual, planta_id)
        SELECT 'DEMO-REP-' || planta_demo, GREATEST(humedad_minima - 10, 0), planta_demo
        FROM cultivo WHERE id = cultivo_id_demo;

        INSERT INTO riego (fecha_hora, cantidad_ml, origen_agua, modo, planta_id)
        VALUES (mes + 4 + time '08:30', 200, cultivo_demo.origen, 'Manual', planta_demo),
               (mes + 14 + time '09:00', 350, cultivo_demo.origen, 'Automatico', planta_demo);

        INSERT INTO alerta (fecha_hora, tipo, mensaje, atendida, planta_id)
        VALUES (mes + 3 + time '08:00', 'Humedad baja',
                'Demo: aviso atendido con el riego del día siguiente.', true, planta_demo),
               (mes + 9 + time '10:00', 'Inspección',
                'Demo: revisión visual del cultivo.', cultivo_demo.meses_atras % 2 = 0, planta_demo),
               (mes + 19 + time '07:45', 'Humedad baja',
                'Demo: lectura inferior al mínimo configurado.', false, planta_demo);
    END LOOP;
END
$demo$;

COMMIT;
