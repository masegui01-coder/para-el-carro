// Genera shared/estimaciones.json: preguntas de estimación calculadas a partir de datos con fuente
// (tipos de cambio, costes de construcción de los colegios de arquitectos, constantes físicas…).
// Así las respuestas no tienen errores de cuentas. Uso: node tools/estimaciones.js
const fs = require('fs');
const path = require('path');

const out = [];
// e: "¿Sabías que…?" que se enseña tras la ronda; w: artículo de Wikipedia (en inglés) para la ilustración
const add = (q, a, u, n, e, w) => out.push({ q, a, u, n, ...(e ? { e } : {}), ...(w ? { w } : {}) });
// Redondeo "humano": 3 cifras significativas (las cifras grandes no se pueden adivinar al euro)
const r3 = (x) => {
  if (x === 0) return 0;
  return Number(Number(x).toPrecision(3));
};
const fmt = (x) => x.toLocaleString('es-ES', { maximumFractionDigits: 2 });

// ------------------------------------------------------------------ Divisas
// Tipos de cambio (unidades por 1 €) del 20/10/2026: BCE y open.er-api.com. Los marcados "fijo" tienen paridad fija con el euro.
const FECHA_CAMBIO = 'cambio del 20/10/2026';
const divisas = [
  ['dongs vietnamitas', 'Vietnam', 29019.19, 100],
  ['rupias indonesias', 'Indonesia', 20170.57, 100],
  ['riales iraníes', 'Irán', 2013339.12, 100],
  ['kips laosianos', 'Laos', 25094.07, 100],
  ['soms uzbekos', 'Uzbekistán', 13604.01, 100],
  ['guaraníes paraguayos', 'Paraguay', 6451.91, 100],
  ['tugriks mongoles', 'Mongolia', 4030.55, 100],
  ['ariarys malgaches', 'Madagascar', 5085.08, 100],
  ['rieles camboyanos', 'Camboya', 4551.11, 100],
  ['pesos colombianos', 'Colombia', 3563.6, 100],
  ['pesos argentinos', 'Argentina', 1708.8, 100],
  ['pesos chilenos', 'Chile', 1102.7, 100],
  ['tenges kazajos', 'Kazajistán', 507.17, 100],
  ['francos guineanos', 'Guinea', 9913.29, 100],
  ['chelines ugandeses', 'Uganda', 4525.07, 100],
  ['chelines tanzanos', 'Tanzania', 2940.11, 100],
  ['libras libanesas', 'Líbano', 100396.05, 100],
  ['kyats birmanos', 'Birmania', 2353.14, 100],
  ['francos burundeses', 'Burundi', 3369.6, 100],
  ['wons surcoreanos', 'Corea del Sur', 1489.17, 100],
  ['yenes japoneses', 'Japón', 177.34, 100],
  ['forintos húngaros', 'Hungría', 364.68, 100],
  ['coronas islandesas', 'Islandia', 136.2, 100],
  ['rupias indias', 'India', 106.459, 100],
  ['bahts tailandeses', 'Tailandia', 37.738, 100],
  ['pesos filipinos', 'Filipinas', 70.804, 100],
  ['liras turcas', 'Turquía', 55.6009, 100],
  ['libras egipcias', 'Egipto', 60.42, 100],
  ['dírhams marroquíes', 'Marruecos', 10.98, 100],
  ['drams armenios', 'Armenia', 406.05, 100],
  ['soms kirguises', 'Kirguistán', 98.15, 100],
  ['rupias nepalíes', 'Nepal', 170.33, 100],
  ['colones costarricenses', 'Costa Rica', 509.9, 100],
  ['pesos dominicanos', 'República Dominicana', 68.99, 100],
  ['nairas nigerianas', 'Nigeria', 1494.62, 100],
  ['birrs etíopes', 'Etiopía', 184.89, 100],
  ['leones de Sierra Leona', 'Sierra Leona', 27.32, 100],
  ['coronas checas', 'República Checa', 24.329, 100],
  ['zlotys polacos', 'Polonia', 4.3768, 100],
  ['rands sudafricanos', 'Sudáfrica', 18.4769, 100],
  ['reales brasileños', 'Brasil', 5.6339, 100],
  ['pesos mexicanos', 'México', 20.7719, 100],
];
// Una curiosidad de cada moneda (y su artículo de Wikipedia, para la ilustración)
const MONEDAS = {
  'dongs vietnamitas': ['En Vietnam casi no se usan monedas: todo se paga con billetes de polímero, y todos llevan la cara de Ho Chi Minh.', 'Ho Chi Minh'],
  'rupias indonesias': ['Indonesia lleva años planeando quitarle tres ceros a la rupia, porque los precios tienen cifras enormes: un café puede costar 30.000 rupias.', 'Indonesian rupiah'],
  'riales iraníes': ['En la calle los iraníes cuentan en «tomanes», que son 10 riales, y el Gobierno ha aprobado quitarle cuatro ceros a la moneda.', 'Iranian rial'],
  'kips laosianos': ['En Laos las monedas casi han desaparecido, y en los sitios turísticos también se aceptan dólares y bahts tailandeses.', 'Lao kip'],
  'soms uzbekos': ['Hasta 2017 el cambio oficial era muy distinto al de la calle, y para pagar algo grande había que llevar fajos de billetes en bolsas.', 'Uzbekistani soʻm'],
  'guaraníes paraguayos': ['Se llama como el pueblo guaraní, cuya lengua es oficial en Paraguay junto al español y la habla la mayoría de la población.', 'Paraguayan guaraní'],
  'tugriks mongoles': ['«Tugrik» significa algo así como «moneda redonda». Los billetes grandes llevan el retrato de Gengis Kan.', 'Mongolian tögrög'],
  'ariarys malgaches': ['Es una de las dos únicas monedas del mundo que no se divide en 100: un ariary son 5 iraimbilanja. La otra es la ouguiya de Mauritania.', 'Malagasy ariary'],
  'rieles camboyanos': ['En Camboya se usan muchísimo los dólares estadounidenses: el riel sirve sobre todo para dar el cambio de las cantidades pequeñas.', 'Cambodian riel'],
  'pesos colombianos': ['El billete de 50.000 pesos lleva a Gabriel García Márquez, y el de 100.000 a Carlos Lleras Restrepo.', 'Colombian peso'],
  'pesos argentinos': ['Desde 1970 Argentina le ha quitado 13 ceros a su moneda, cambiándole de nombre varias veces (peso ley, peso argentino, austral, peso).', 'Argentine peso'],
  'pesos chilenos': ['En Chile a 1.000 pesos se les llama «una luca». El peso chileno no tiene céntimos en circulación.', 'Chilean peso'],
  'tenges kazajos': ['El tenge nació en 1993, tras la URSS, y sus billetes han ganado varias veces el premio al «billete del año» de los coleccionistas.', 'Kazakhstani tenge'],
  'francos guineanos': ['Guinea tiene algunas de las mayores reservas de bauxita del mundo (de donde sale el aluminio), pero su moneda es de las de menor valor de África.', 'Guinean franc'],
  'chelines ugandeses': ['Se llama «chelín» por el chelín de África Oriental de la época británica. Uganda es la «perla de África» y tiene la mitad de los gorilas de montaña.', 'Ugandan shilling'],
  'chelines tanzanos': ['En suajili se llama «shilingi». Tanzania tiene el Kilimanjaro, la montaña más alta de África, y el parque del Serengeti.', 'Tanzanian shilling'],
  'libras libanesas': ['Durante décadas se cambió a 1.507,5 por dólar, pero con la crisis que empezó en 2019 perdió más del 95% de su valor.', 'Beirut'],
  'kyats birmanos': ['En 1987 el dictador Ne Win anuló billetes y sacó otros de 45 y 90 kyats, porque su número de la suerte era el 9.', 'Burmese kyat'],
  'francos burundeses': ['Burundi es de los países con menor renta del mundo, y el café es su principal exportación.', 'Burundian franc'],
  'wons surcoreanos': ['El billete de 10.000 wones lleva al rey Sejong, que en el siglo XV creó el alfabeto coreano, el hangul.', 'South Korean won'],
  'yenes japoneses': ['Las monedas de 5 y 50 yenes tienen un agujero. La de 5 se dice «go-en», que suena como «buena relación», y se deja como ofrenda en los templos.', 'Japanese yen'],
  'forintos húngaros': ['El nombre viene del florín de Florencia, una famosa moneda de oro medieval. Hungría está en la UE, pero no ha adoptado el euro.', 'Hungarian forint'],
  'coronas islandesas': ['Islandia es de los países que menos efectivo usa del mundo. Sus monedas llevan animales marinos como el bacalao, el cangrejo o el delfín.', 'Icelandic króna'],
  'rupias indias': ['En 2016 el Gobierno anuló de un día para otro los billetes de 500 y 1.000 rupias, más del 80% del dinero en efectivo del país.', 'Indian rupee'],
  'bahts tailandeses': ['Pisar un billete o una moneda en Tailandia es una gran falta de respeto, porque llevan la imagen del rey.', 'Thai baht'],
  'pesos filipinos': ['Su nombre viene de la época española. En filipino se dice «piso», y su símbolo es ₱.', 'Philippine peso'],
  'liras turcas': ['En 2005 Turquía le quitó seis ceros a la lira: un millón de liras antiguas pasó a ser una lira nueva.', 'Turkish lira'],
  'libras egipcias': ['Sus billetes van en árabe por un lado y en inglés por el otro, y muchos muestran templos y monumentos faraónicos.', 'Egyptian pound'],
  'dírhams marroquíes': ['Es una moneda casi cerrada: no se puede sacar de Marruecos, así que hay que cambiar el dinero al llegar al país.', 'Moroccan dirham'],
  'drams armenios': ['«Dram» significa «dinero» y viene de la misma raíz que el dracma griego y el dírham árabe.', 'Armenian dram'],
  'soms kirguises': ['En 1993 Kirguistán fue el primer país de Asia Central en tener moneda propia tras la URSS. «Som» significa «puro», como el oro puro.', 'Kyrgyzstani som'],
  'rupias nepalíes': ['Tiene un cambio fijo con la rupia india: 1,6 rupias nepalíes por cada una. Nepal también es famoso por tener la única bandera nacional que no es rectangular.', 'Nepalese rupee'],
  'colones costarricenses': ['Se llama así por Cristóbal Colón. Sus billetes muestran la fauna del país, como el perezoso, el colibrí o el tiburón toro.', 'Costa Rican colón'],
  'pesos dominicanos': ['La República Dominicana comparte la isla de La Española con Haití, cuya moneda es la gourde.', 'Dominican peso'],
  'nairas nigerianas': ['Nigeria es el país más poblado de África. En 2023 el cambio a billetes nuevos causó una grave escasez de efectivo.', 'Nigerian naira'],
  'birrs etíopes': ['Etiopía usa su propio calendario, de 13 meses, y va unos 7 u 8 años por detrás del nuestro.', 'Ethiopian birr'],
  'leones de Sierra Leona': ['En 2022 se creó el «nuevo leone» quitando tres ceros: 1.000 leones antiguos pasaron a ser 1.', 'Sierra Leonean leone'],
  'coronas checas': ['La República Checa está en la Unión Europea, pero mantiene su corona y no ha adoptado el euro.', 'Czech koruna'],
  'zlotys polacos': ['«Złoty» significa «dorado». Polonia está en la UE desde 2004, pero sigue con su propia moneda.', 'Warsaw'],
  'rands sudafricanos': ['Se llama así por el Witwatersrand, la cordillera donde se descubrió la mayor reserva de oro del mundo. De allí viene también la moneda de oro Krugerrand.', 'South African rand'],
  'reales brasileños': ['Nació en 1994 con el Plan Real, que acabó con una hiperinflación brutal. Sus billetes llevan animales como el jaguar o la tortuga marina.', 'Brazilian real'],
  'pesos mexicanos': ['El símbolo del dólar ($) viene en realidad del peso hispanoamericano, que se usaba en medio mundo en los siglos XVII y XVIII.', 'Mexican peso'],
  'escudos caboverdianos': ['Primero tuvo cambio fijo con el escudo portugués y después con el euro. Cabo Verde es un archipiélago volcánico frente a Senegal.', 'Cape Verdean escudo'],
  'francos CFA': ['Son dos monedas, la de África Occidental y la de África Central, y Francia garantizaba su cambio. La occidental tiene previsto pasar a llamarse «eco».', 'CFA franc'],
  'francos comorenses': ['Las Comoras son de los mayores productores del mundo de ylang-ylang, una flor que se usa en perfumes muy famosos.', 'Cananga odorata'],
  'dobras': ['Santo Tomé y Príncipe es de los países más pequeños de África. En 2018 la dobra perdió tres ceros.', 'São Tomé and Príncipe dobra'],
  'marcos convertibles': ['Se llama así por el marco alemán, al que estaba ligado 1 a 1. Cada una de las dos entidades del país usa diseños distintos en sus billetes.', 'Bosnia and Herzegovina convertible mark'],
};
const curiosidad = (moneda) => MONEDAS[moneda] || [];

for (const [moneda, pais, tasa, euros] of divisas) {
  add(`Si cambias ${euros} € a ${moneda} (${pais}), ¿cuántos te dan?`, r3(tasa * euros), moneda,
    `A 1 € = ${fmt(tasa)} ${moneda} (${FECHA_CAMBIO}, sin comisiones).`, ...curiosidad(moneda));
}
const fijas = [
  ['escudos caboverdianos', 'Cabo Verde', 110.265],
  ['francos CFA', 'Senegal, Costa de Marfil y otros 12 países de África', 655.957],
  ['francos comorenses', 'Comoras', 491.96775],
  ['dobras', 'Santo Tomé y Príncipe', 24.5],
  ['marcos convertibles', 'Bosnia y Herzegovina', 1.95583],
];
for (const [moneda, pais, tasa] of fijas) {
  add(`Si cambias 100 € a ${moneda} (${pais}), ¿cuántos te dan?`, Math.round(tasa * 100 * 100) / 100, moneda,
    `Tiene cambio fijo con el euro: 1 € = ${fmt(tasa)} ${moneda}.`, ...curiosidad(moneda));
}

// ------------------------------------------------------------------ Construcción (sin suelo)
// Costes de ejecución material de referencia 2026 de los Colegios de Arquitectos de Málaga y Cádiz.
// No incluyen suelo, licencias, IVA, honorarios ni beneficio del constructor: el precio final real es mayor.
const APRENDE_OBRA = 'Ese es el coste de la obra en sí. Al precio final hay que sumarle los honorarios del arquitecto, las licencias y los impuestos, así que suele salir bastante más caro. Y en una ciudad, el suelo puede costar tanto como el edificio.';
const NOTA_OBRA = (colegio, eur) => `Coste de referencia del Colegio de Arquitectos de ${colegio} 2026: ${fmt(eur)} €/m² (ejecución material, sin suelo, licencias, IVA ni honorarios).`;
const obras = [
  ['Málaga', 'un bloque de pisos entre medianeras de 4 plantas y 1.000 m² construidos', 954, 1000],
  ['Málaga', 'un edificio de viviendas aislado de 4 plantas y 1.200 m²', 1023, 1200],
  ['Málaga', 'un chalet aislado de 180 m²', 1341, 180],
  ['Málaga', 'una casa adosada de 140 m²', 1104, 140],
  ['Málaga', 'un hotel de 5 estrellas de 8.000 m²', 2456, 8000],
  ['Málaga', 'un hotel de 3 estrellas de 5.000 m²', 1622, 5000],
  ['Málaga', 'una piscina descubierta de 8 × 4 metros de lámina de agua', 607, 32],
  ['Málaga', 'un hospital de 20.000 m²', 1995, 20000],
  ['Málaga', 'un colegio de 3.000 m²', 1067, 3000],
  ['Málaga', 'un campo de golf de 60 hectáreas', 54, 600000],
  ['Málaga', 'una discoteca de 800 m²', 1353, 800],
  ['Málaga', 'un bar de 120 m²', 1205, 120],
  ['Málaga', 'una nave industrial sencilla de 1.000 m²', 391, 1000],
  ['Málaga', 'una iglesia de 600 m²', 1625, 600],
  ['Málaga', 'una residencia de ancianos de 4.000 m²', 1268, 4000],
  ['Málaga', 'una terminal de aeropuerto de 50.000 m²', 2865, 50000],
  ['Málaga', 'un tanatorio de 1.500 m²', 1344, 1500],
  ['Málaga', 'un gimnasio de 1.000 m²', 999, 1000],
  ['Cádiz', 'un bloque de pisos entre medianeras de 4 plantas y 1.000 m² construidos', 813.94, 1000],
  ['Cádiz', 'un chalet aislado de 150 m²', 991.98, 150],
  ['Cádiz', 'un garaje subterráneo de 2.500 m²', 508.71, 2500],
  ['Cádiz', 'un teatro de 3.000 m²', 2034.84, 3000],
  ['Cádiz', 'un hotel de 5 estrellas de 8.000 m²', 1913.1, 8000],
  ['Cádiz', 'un multicine de 2.000 m²', 1322.65, 2000],
  ['Cádiz', 'un supermercado de 1.500 m²', 763.07, 1500],
  ['Cádiz', 'un restaurante de 200 m²', 915.68, 200],
];
for (const [colegio, que, eur, m2] of obras) {
  add(`Aproximadamente, ¿cuántos euros cuesta construir en la provincia de ${colegio} ${que}, sin contar el suelo?`,
    r3(eur * m2), '€', NOTA_OBRA(colegio, eur), APRENDE_OBRA);
}

// ------------------------------------------------------------------ Cálculos curiosos
const POB_ESPANA = 49_100_000; // INE, 1 de enero de 2025 (aprox.)
const PIKACHU_EUR = 16_492_000 / 1.1215; // récord de 2026 en dólares, al cambio del 20/10/2026
const SALARIO_MEDIO = 28_050; // salario medio bruto anual en España (INE, encuesta 2023)
const calc = [
  ['¿Cuántos segundos tiene un año (no bisiesto)?', 365 * 86400, 'segundos', '365 × 24 × 60 × 60.', true],
  ['¿Cuántos segundos ha vivido una persona que cumple 30 años?', 30 * 365.25 * 86400, 'segundos', 'Casi mil millones.'],
  ['¿Cuántas veces late el corazón en un día (a 70 pulsaciones por minuto)?', 70 * 1440, 'latidos', '70 × 1.440 minutos.', true],
  ['¿Cuántas veces respiramos al día (a 15 respiraciones por minuto)?', 15 * 1440, 'respiraciones', '', true],
  ['¿Cuántas veces parpadeamos en un año?', 15000 * 365, 'parpadeos', 'A unos 15.000 parpadeos al día.'],
  ['¿Cuántas veces cabe España en Rusia?', 17098246 / 505990, 'veces', 'Rusia: 17.098.246 km²; España: 505.990 km².'],
  ['¿Cuántas veces cabe Andorra en España?', 505990 / 468, 'veces', 'Andorra mide 468 km².'],
  ['¿Cuántas Ciudades del Vaticano caben en el municipio de Madrid?', 604.3 / 0.49, 'veces', 'Vaticano: 0,49 km²; Madrid: 604,3 km².'],
  ['¿Cuántas Lunas cabrían dentro de la Tierra?', 49, 'lunas', 'Por volumen, unas 49.'],
  ['¿Cuántas Tierras cabrían dentro del Sol?', 1300000, 'tierras', 'Por volumen, alrededor de 1,3 millones.'],
  ['¿Cuántas piscinas olímpicas harían falta para vaciar el lago Ness?', 7.4e12 / 2.5e6, 'piscinas', '7,4 billones de litros entre 2,5 millones.'],
  ['¿Cuántos estadios Santiago Bernabéu llenos harían falta para meter a toda la población de España?', POB_ESPANA / 83186, 'estadios', 'Unos 49,1 millones de personas y 83.186 asientos.'],
  ['¿Cuántos días tardarías en contar hasta un millón, diciendo un número por segundo sin parar?', 1e6 / 86400, 'días', ''],
  ['¿Cuántos años tardarías en contar hasta mil millones, diciendo un número por segundo sin parar?', 1e9 / (365.25 * 86400), 'años', ''],
  ['¿Cuántos kilos pesa un millón de euros en monedas de 1 céntimo?', 1e8 * 2.3 / 1000, 'kg', 'Cada moneda de 1 céntimo pesa 2,30 g.'],
  ['¿Cuántos kilos pesa un millón de euros en monedas de 1 €?', 1e6 * 7.5 / 1000, 'kg', 'Cada moneda de 1 € pesa 7,5 g.'],
  ['Si alguien de 70 kg se pesara en monedas de 2 €, ¿cuántos euros valdría?', (70000 / 8.5) * 2, '€', 'Cada moneda de 2 € pesa 8,5 g.'],
  ['¿Cuántos kilómetros recorre la Tierra alrededor del Sol en un solo día?', 107000 * 24, 'km', 'A unos 107.000 km/h.'],
  ['¿Cuántas vueltas a la Tierra da la luz en un segundo?', 299792 / 40075, 'vueltas', ''],
  ['¿Cuántos días tardarías en llegar a la Luna en coche a 120 km/h sin parar?', 384400 / 120 / 24, 'días', ''],
  ['¿Cuántos años tardarías en llegar al Sol en coche a 120 km/h sin parar?', 149600000 / 120 / 24 / 365.25, 'años', ''],
  ['¿Cuántos días tardarías en llegar a la Luna andando a 5 km/h sin parar?', 384400 / 5 / 24, 'días', 'Más de 8 años andando.'],
  ['¿Cuántos pasos harían falta para dar la vuelta a la Tierra por el ecuador (pasos de 75 cm)?', 40075000 / 0.75, 'pasos', ''],
  ['¿Cuántas vueltas a un campo de fútbol (105 × 68 m) hay que dar para correr un maratón?', 42195 / (2 * (105 + 68)), 'vueltas', ''],
  ['¿Cuántos campos de fútbol caben en Mónaco?', 2.02e6 / (105 * 68), 'campos', 'Mónaco mide 2,02 km².'],
  ['¿Cuántos campos de fútbol caben en la Ciudad del Vaticano?', 0.49e6 / (105 * 68), 'campos', ''],
  ['¿Cuántos habitantes por km² hay en Mónaco?', 38000 / 2.02, 'hab./km²', 'El país más densamente poblado del mundo.'],
  ['Si se repartiera España entre todos sus habitantes, ¿cuántos m² tocarían por persona?', 505990e6 / POB_ESPANA, 'm²', ''],
  ['¿Cuántas latas de refresco de 33 cl hacen falta para llenar una bañera de 200 litros?', 200 / 0.33, 'latas', ''],
  ['¿Cuántas pelotas de tenis cabrían en una piscina olímpica?', 2.5e9 * 0.64 / ((4 / 3) * Math.PI * 3.35 ** 3), 'pelotas', 'Unos 10 millones, contando los huecos entre pelotas.'],
  ['¿Cuántos granos de arroz hay en un kilo?', 1000 / 0.02, 'granos', 'A unos 0,02 g por grano.'],
  ['¿Cuántos cafés espresso salen de un kilo de café?', 1000 / 7, 'cafés', 'A unos 7 g por café.'],
  ['¿Cuántas veces late el corazón de un colibrí en un día?', 1200 * 1440, 'latidos', ''],
  ['¿Cuántos días vive una persona que llega a los 80 años?', 80 * 365.25, 'días', '', true],
  ['¿Cuántas horas tiene un año bisiesto?', 366 * 24, 'horas', '', true],
  ['¿Cuántos minutos tiene una semana?', 7 * 1440, 'minutos', '', true],
  ['¿Cuántos litros de sangre bombea el corazón en 80 años?', 7500 * 365.25 * 80, 'litros', 'A unos 7.500 litros al día.'],
  ['¿Cuántos años tendría que trabajar alguien con el salario medio español para comprar la carta de Pokémon más cara?', PIKACHU_EUR / SALARIO_MEDIO, 'años', 'Unos 14,7 millones de € entre unos 28.050 € brutos al año, sin gastar nada.'],
  ['¿Cuántos décimos de la Lotería de Navidad (20 €) se podrían comprar con lo que costó la carta de Pokémon más cara?', PIKACHU_EUR / 20, 'décimos', ''],
  ['¿Cuántos euros cuesta llenar una piscina olímpica con agua del grifo en España?', 2500 * 2.1, '€', 'A unos 2,1 € por m³ de agua (precio medio en España).'],
  ['¿Cuántos litros de gasolina gasta un coche de Madrid a Barcelona (620 km a 6 L/100 km)?', 620 * 6 / 100, 'litros', ''],
  ['¿Cuántas personas caben de pie en un campo de fútbol (a 4 personas por m²)?', 105 * 68 * 4, 'personas', ''],
  ['¿Cuántos folios A4 hacen falta para cubrir un campo de fútbol?', (105 * 68) / (0.21 * 0.297), 'folios', ''],
  ['¿Cuántas horas de vídeo se suben a YouTube en un día?', 500 * 1440, 'horas', 'Se suben unas 500 horas cada minuto.'],
  ['¿Cuántos años tardaría una persona en ver todo lo que se sube a YouTube en un solo día?', (500 * 1440) / (24 * 365.25), 'años', 'Viéndolo 24 horas al día.'],
  ['¿Cuántas vueltas a la Tierra da la Estación Espacial Internacional en una semana?', 16 * 7, 'vueltas', 'Da unas 16 vueltas al día.', true],
  ['¿Cuántos kilómetros recorre la Estación Espacial Internacional en un día?', 27600 * 24, 'km', 'A unos 27.600 km/h.'],
  ['¿Cuántos euros hay en una fila de monedas de 1 € puesta de Madrid a Barcelona (505 km en línea recta)?', 505000 / 0.02325, '€', 'Cada moneda mide 23,25 mm.'],
  ['¿Cuántos billetes de 10 € puestos en fila hacen falta para dar la vuelta a la Tierra?', 40075000 / 0.127, 'billetes', 'Un billete de 10 € mide 12,7 cm de largo.'],
  ['¿Cuántas veces cabe la altura de la Torre Eiffel en la altura del Everest?', 8849 / 330, 'veces', 'Everest 8.849 m; Torre Eiffel 330 m.'],
  ['¿Cuántas personas de 1,70 m subidas una encima de otra igualarían la altura del Everest?', 8849 / 1.7, 'personas', ''],
  ['¿Cuántos pisos de 3 metros tendría un edificio tan alto como el Teide?', 3715 / 3, 'pisos', ''],
  ['Si la historia de la Tierra fuera un día de 24 horas, ¿cuántos segundos antes de medianoche aparecerían los humanos?', 300000 / 4.54e9 * 86400, 'segundos', 'Homo sapiens tiene unos 300.000 años; la Tierra, 4.540 millones.'],
  ['¿Cuántos kilos de comida come un elefante en un año?', 150 * 365, 'kg', 'Unos 150 kg al día.'],
  ['¿Cuántas toneladas de caca hace un elefante en un año?', 80 * 365 / 1000, 'toneladas', 'Unos 80 kg al día.'],
  ['¿Cuántos huevos pone una gallina ponedora en toda su vida productiva (unos 2 años)?', 300 * 2, 'huevos', '', true],
  ['¿Cuántos litros de leche da una vaca lechera española en un año?', 30 * 305, 'litros', 'Unos 30 L al día durante unos 305 días de lactancia.'],
  ['¿Cuántos kilómetros mide el ADN de una persona si estiráramos el de todas sus células?', 2 * 37e12 / 1000, 'km', 'Unos 2 m por célula y unos 37 billones de células.'],
  ['¿Cuántas veces iría y volvería al Sol el ADN estirado de una persona?', (2 * 37e12 / 1000) / (2 * 149.6e6), 'viajes', 'Unos 74.000 millones de km de ADN.'],
  ['¿Cuántos días de sueldo medio español cuesta un décimo de la Lotería de Navidad?', 20 / (SALARIO_MEDIO / 365), 'días', ''],
  ['¿Cuántas combinaciones distintas tiene el Euromillones?', 139838160, 'combinaciones', 'C(50,5) × C(12,2).', true],
  ['¿Cuántas combinaciones distintas tiene la Primitiva (6 números de 49)?', 13983816, 'combinaciones', '', true],
  ['¿Cuántas quinielas distintas se pueden rellenar con 15 partidos (1, X o 2)?', 3 ** 15, 'quinielas', '', true],
  ['¿Cuántas manos distintas de 5 cartas se pueden sacar de una baraja de póker (52 cartas)?', 2598960, 'manos', '', true],
  ['¿Cuántas formas distintas hay de ordenar las 10 primeras cartas de una baraja?', 3628800, 'formas', '10! = 3.628.800.', true],
  ['¿Cuántas matrículas de coche distintas permite el sistema español (4 números y 3 letras)?', 10000 * 20 ** 3, 'matrículas', 'Se usan 20 consonantes (sin vocales ni Ñ ni Q).', true],
  ['¿Cuántos números de teléfono móvil de 9 cifras empezando por 6 o 7 hay en España?', 2 * 1e8, 'números', '', true],
  ['¿Cuántos años tardaría en gastar 1.000 millones de euros alguien que gastase 1.000 € al día?', 1e9 / 1000 / 365.25, 'años', ''],
  ['¿Cuántos kilos pesa toda la humanidad junta?', 8.2e9 * 62, 'kg', 'Unos 8.200 millones de personas a unos 62 kg de media.'],
  ['¿Cuántos estadios Camp Nou llenaría la población de Madrid capital?', 3460000 / 105000, 'estadios', 'Unos 3,46 millones de habitantes y unos 105.000 asientos.'],
  ['¿Cuántas toneladas pesa el agua de una piscina olímpica?', 2500, 'toneladas', '2.500 m³ de agua.'],
  ['¿Cuántos kilómetros de largo mediría una fila con todos los coches de España (unos 26 millones, de 4,3 m)?', 26e6 * 4.3 / 1000, 'km', 'Casi tres vueltas a la Tierra.'],
  ['¿Cuántas vueltas a la Tierra darían todos los coches de España puestos en fila?', 26e6 * 4.3 / 1000 / 40075, 'vueltas', ''],
  ['¿Cuántas horas tarda en llegar a la Tierra la luz del Sol que se refleja en Plutón?', 5.9e9 / 299792 / 3600, 'horas', 'Plutón está a unos 5.900 millones de km del Sol.'],
  ['¿Cuántos segundos tarda la luz de la Luna en llegar a la Tierra?', 384400 / 299792, 'segundos', ''],
  ['¿Cuántos días tardaría en llegar a la Luna un avión comercial a 900 km/h?', 384400 / 900 / 24, 'días', ''],
  ['¿Cuántos años tardaría la sonda Voyager 1 (61.000 km/h) en llegar a la estrella más cercana?', 4.24 * 9.461e12 / 61000 / 24 / 365.25, 'años', 'Próxima Centauri está a 4,24 años luz.'],
  ['¿Cuántos euros cobra por segundo alguien que gane 100 millones al año?', 1e8 / (365 * 86400), '€', ''],
  ['¿Cuántos años tendría que trabajar alguien con el salario medio español para ganar lo que Neymar costó al PSG?', 222e6 / SALARIO_MEDIO, 'años', '222 millones de € en 2017.'],
  ['¿Cuántos jamones ibéricos de 400 € se podrían comprar con el traspaso de Neymar al PSG?', 222e6 / 400, 'jamones', ''],
  ['¿Cuántos Chupa Chups de 0,30 € se podrían comprar con lo que costó el Salvator Mundi de Leonardo?', (450.3e6 / 1.1215) / 0.3, 'Chupa Chups', '450,3 millones de dólares al cambio de octubre de 2026.'],
];
// exacto = true: la cifra es exacta (combinaciones, minutos…) y no se redondea
for (const [q, a, u, n, exacto] of calc) add(q, exacto ? a : r3(a), u, n);

fs.writeFileSync(path.join(__dirname, '..', 'shared', 'estimaciones.json'), JSON.stringify(out, null, 1));
console.log(`${out.length} preguntas de estimación`);
