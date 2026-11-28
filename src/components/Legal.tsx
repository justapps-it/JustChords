import { TERMS_VERSION } from '../api';
import { go } from '../router';
import { useSettings } from '../settings';

const REPO = 'https://github.com/justapps-it/Just-Chords';
const CONTACT = 'info@ticly.it';

type Doc = { title: string; sections: [string, string[]][] };

const PRIVACY: Record<'it' | 'en', Doc> = {
  it: {
    title: 'Informativa sulla privacy',
    sections: [
      ['Chi tratta i dati', [`Just-Chords è un’app di JustApps (www.justapps.it), un progetto di ticly (www.ticly.it), che è titolare del trattamento. Per qualsiasi richiesta sulla privacy scrivici a ${CONTACT}.`]],
      [
        'Senza account',
        [
          'Canzoni, scalette, impostazioni e profilo dei gusti restano sul tuo dispositivo (memoria del browser o dell’app). Non li riceviamo.',
          'Quando cerchi una canzone, apri una pagina di accordi o incolli un link, il testo cercato o il link passa dal nostro server per interrogare i siti indicati sotto. Le risposte possono restare in una cache anonima fino a 7 giorni, senza alcun collegamento a te.',
          'Per i consigli inviamo al server il profilo dei gusti (generi, livello, artisti preferiti) e i titoli che hai già, solo per calcolare la risposta: non vengono salvati.',
        ],
      ],
      [
        'Con un account (Google o email)',
        [
          'Da Google riceviamo identificativo dell’account, nome, indirizzo email e foto del profilo. Non riceviamo la password né altri dati del tuo account Google.',
          'Con l’accesso via email salviamo email, nome e la password solo in forma cifrata (hash PBKDF2 con sale): nessuno, nemmeno noi, può leggerla. Per proteggere gli account teniamo per un’ora il numero di tentativi di accesso di ogni indirizzo IP.',
          'Nel profilo salviamo anche il livello, le lezioni di “Impara” completate e la data in cui hai risposto alle domande iniziali.',
          'Salviamo questi dati insieme a profilo dei gusti, libreria, scalette e alla data e versione del consenso che dai all’accesso, per sincronizzarli tra i tuoi dispositivi.',
          'Base giuridica: l’esecuzione del servizio che richiedi creando l’account (art. 6.1.b GDPR).',
        ],
      ],
      [
        'Servizi esterni',
        [
          'Cloudflare: ospita sito, server e dati degli account; tratta gli indirizzi IP nei log tecnici. Può trasferire dati fuori dall’UE con le garanzie previste dal GDPR (clausole contrattuali standard, EU-US Data Privacy Framework).',
          'Google: accesso con Google; la ricerca dei video su YouTube avviene dal nostro server e invia a Google solo titolo e artista.',
          'YouTube: il player (youtube-nocookie.com) si carica solo quando premi “Ascolta”; da quel momento YouTube riceve il tuo indirizzo IP e applica la propria informativa.',
          'TikTok e Instagram: in “Analizza video” il video si vede con il player ufficiale della piattaforma, caricato dal tuo dispositivo; la piattaforma riceve il tuo indirizzo IP e applica la propria informativa. Il nostro server non scarica i video.',
          'Analizza video: accordi, tonalità e tempo si calcolano sul tuo dispositivo. Con “Analizza dal link” l’app Android scarica direttamente sul telefono solo l’audio del video da YouTube, TikTok o Instagram (che ricevono il tuo indirizzo IP), lo analizza e lo cancella subito: non passa dal nostro server. In alternativa, per sentire il video che suona nell’app, usa la cattura dell’audio di Android (ti chiede il permesso di registrare lo schermo): l’audio resta in memoria e non lascia il telefono.',
          'Testo sincronizzato: solo se lo attivi, l’audio analizzato viene inviato a pezzi di 30 secondi al nostro server e trascritto con Whisper su Cloudflare Workers AI. Non viene salvato né usato per altro; il testo trascritto resta solo nella canzone che salvi. Per evitare abusi teniamo per un giorno quanti secondi ha trascritto ogni indirizzo IP.',
          'Playlist: se incolli il link di una playlist, il nostro server legge quella playlist pubblica da Spotify, YouTube o Deezer per trovare artisti e generi. Teniamo solo la risposta in una cache anonima per al massimo un giorno; artisti e generi finiscono nel tuo profilo dei gusti.',
          'Deezer: per i consigli il nostro server invia solo nomi di artisti e brani. Le copertine sono caricate dal tuo dispositivo dai server di Deezer, che ricevono il tuo indirizzo IP.',
          'Ultimate Guitar e Accordi e Spartiti: le pagine che scegli sono scaricate dal nostro server o, nell’app Android, direttamente dal tuo telefono.',
        ],
      ],
      [
        'Per quanto tempo',
        ['I dati dell’account restano finché lo tieni. “Elimina account” (in Profilo) cancella subito dal server tutti i dati collegati. I dati sul dispositivo restano finché non li cancelli tu.'],
      ],
      [
        'I tuoi diritti',
        [
          'Puoi chiedere accesso, rettifica, cancellazione, limitazione, portabilità dei dati e opporti al trattamento. Dal Profilo puoi scaricare i tuoi dati in un file ed eliminare l’account da solo.',
          'Puoi presentare reclamo al Garante per la protezione dei dati personali (www.garanteprivacy.it).',
        ],
      ],
      ['Minori', ['L’account è riservato a chi ha almeno 14 anni (art. 8 GDPR e art. 2-quinquies del Codice privacy). Senza account Just-Chords si usa liberamente.']],
      ['Cookie e pubblicità', ['Just-Chords non usa cookie propri, non mostra pubblicità, non usa sistemi di tracciamento o profilazione pubblicitaria e non vende dati. Usa la memoria locale del browser solo per funzionare.']],
      ['Modifiche', [`Versione del ${TERMS_VERSION}. Se cambia in modo rilevante, te lo chiederemo di nuovo al prossimo accesso.`]],
    ],
  },
  en: {
    title: 'Privacy policy',
    sections: [
      ['Who handles your data', [`Just-Chords is an app by JustApps (www.justapps.it), a ticly project (www.ticly.it), which is the data controller. For any privacy request, write to us at ${CONTACT}.`]],
      [
        'Without an account',
        [
          'Songs, setlists, settings and your taste profile stay on your device (browser or app storage). We don’t receive them.',
          'When you search a song, open a chords page or paste a link, the search text or link goes through our server to query the sites listed below. Answers may stay in an anonymous cache for up to 7 days, not linked to you.',
          'For recommendations the server receives your taste profile (genres, level, favourite artists) and the titles you already have, only to compute the answer: they are not stored.',
        ],
      ],
      [
        'With an account (Google or email)',
        [
          'Google gives us your account ID, name, email address and profile picture. We don’t receive your password or other data of your Google account.',
          'With email sign-in we store your email, name and your password only in hashed form (salted PBKDF2): nobody, not even us, can read it. To protect accounts we keep the number of sign-in attempts per IP address for one hour.',
          'Your profile also holds your level, the “Learn” lessons you completed and when you answered the first-run questions.',
          'We store them with your taste profile, library, setlists and the date and version of the consent you give at sign-in, to sync them across your devices.',
          'Legal basis: performing the service you ask for by creating the account (art. 6.1.b GDPR).',
        ],
      ],
      [
        'Third-party services',
        [
          'Cloudflare: hosts the site, server and account data; processes IP addresses in technical logs. It may transfer data outside the EU with the safeguards required by the GDPR (standard contractual clauses, EU-US Data Privacy Framework).',
          'Google: sign-in with Google; YouTube video search runs on our server and sends Google only title and artist.',
          'YouTube: the player (youtube-nocookie.com) loads only when you press “Listen”; from then on YouTube receives your IP address and its own policy applies.',
          'TikTok and Instagram: in “Analyse a video” the video plays in the platform’s official player, loaded by your device; the platform receives your IP address and its own policy applies. Our server does not download videos.',
          'Analyse a video: chords, key and tempo are worked out on your device. With “Analyse from the link” the Android app downloads only the video’s audio from YouTube, TikTok or Instagram (which receive your IP address) straight to the phone, analyses it and deletes it right away: it does not go through our server. Alternatively, to hear the video playing inside the app, it uses Android’s audio playback capture (it asks to record the screen): the audio stays in memory and never leaves the phone.',
          'Synced lyrics: only if you turn it on, the analysed audio is sent in 30-second pieces to our server and transcribed with Whisper on Cloudflare Workers AI. It is not stored or used for anything else; the transcribed lyrics only end up in the song you save. To prevent abuse we keep, for one day, how many seconds each IP address has transcribed.',
          'Playlist: if you paste a playlist link, our server reads that public playlist from Spotify, YouTube or Deezer to find artists and genres. We keep only the answer in an anonymous cache for up to a day; artists and genres go into your taste profile.',
          'Deezer: for recommendations our server sends only artist and song names. Covers load on your device from Deezer’s servers, which receive your IP address.',
          'Ultimate Guitar and Accordi e Spartiti: the pages you pick are downloaded by our server or, in the Android app, by your phone itself.',
        ],
      ],
      ['How long', ['Account data stays as long as you keep the account. “Delete account” (in Profile) immediately erases from the server all data linked to it. Data on your device stays until you delete it.']],
      [
        'Your rights',
        [
          'You can ask for access, rectification, erasure, restriction, data portability and object to processing. From Profile you can download your data as a file and delete your account yourself.',
          'You can lodge a complaint with your data protection authority (in Italy: www.garanteprivacy.it).',
        ],
      ],
      ['Children', ['Accounts are for people aged 14 or over (art. 8 GDPR and art. 2-quinquies of the Italian privacy code). Without an account Just-Chords can be used freely.']],
      ['Cookies and ads', ['Just-Chords sets no cookies of its own, shows no ads, uses no tracking or ad profiling and sells no data. It uses the browser’s local storage only to work.']],
      ['Changes', [`Version of ${TERMS_VERSION}. If it changes materially, we will ask you again at your next sign-in.`]],
    ],
  },
};

// What Google Play asks for: how to delete the account and its data.
const DELETE: Record<'it' | 'en', Doc> = {
  it: {
    title: 'Eliminare l’account Just-Chords',
    sections: [
      ['Dall’app o dal sito', ['Apri Profilo, accedi con il tuo account e premi “Elimina account”. Account, profilo, libreria sincronizzata e progressi vengono cancellati subito e per sempre dai nostri server. Le canzoni salvate sul tuo dispositivo restano lì finché non disinstalli l’app o cancelli i dati del browser.']],
      ['Via email', [`Se non riesci ad accedere, scrivi a ${CONTACT} dall’indirizzo dell’account chiedendo l’eliminazione: cancelliamo tutto entro 30 giorni e ti confermiamo l’avvenuta eliminazione.`]],
      ['Cosa viene eliminato', ['Nome, email, foto del profilo, password cifrata (accesso via email), profilo dei gusti, livello e lezioni completate, libreria e scalette sincronizzate, data del consenso. Non conserviamo copie: gli unici dati che restano sono contatori anonimi per indirizzo IP che scadono entro un giorno.']],
    ],
  },
  en: {
    title: 'Delete your Just-Chords account',
    sections: [
      ['From the app or the website', ['Open Profile, sign in and tap “Delete account”. Your account, profile, synced library and progress are deleted from our servers at once and for good. Songs saved on your device stay there until you uninstall the app or clear the browser data.']],
      ['By email', [`If you can’t sign in, write to ${CONTACT} from the account’s address asking for deletion: we delete everything within 30 days and confirm it.`]],
      ['What is deleted', ['Name, email, profile picture, hashed password (email sign-in), taste profile, level and completed lessons, synced library and setlists, consent date. We keep no copies: the only data left are anonymous per-IP counters that expire within a day.']],
    ],
  },
};

const TERMS: Record<'it' | 'en', Doc> = {
  it: {
    title: 'Termini di utilizzo',
    sections: [
      ['Il servizio', [`Just-Chords è un’app gratuita e open source (licenza GPL-3.0, ${REPO}) di JustApps, un progetto di ticly. Contatti: ${CONTACT}. È fornita “così com’è”, senza garanzie di disponibilità o di esattezza di accordi, livelli e consigli.`]],
      ['Età', ['Per creare un account devi avere almeno 14 anni. Confermandolo all’accesso dichiari che è vero.']],
      [
        'Contenuti di terzi',
        [
          'Accordi e testi appartengono ai rispettivi autori, editori e a chi li ha trascritti. Just-Chords apre solo le pagine che scegli tu, per il tuo uso personale, e ne conserva il link alla fonte.',
          'Rispetta i termini dei siti di origine e non ripubblicare i contenuti. Chi ne è titolare può chiederne la rimozione tramite i contatti nell’informativa privacy.',
        ],
      ],
      ['Uso corretto', ['Non usare Just-Chords in modo automatizzato o massivo, per scaricare grandi quantità di pagine o per sovraccaricare il servizio o i siti di origine.']],
      ['Account', ['Puoi eliminare l’account quando vuoi dal Profilo. Possiamo sospendere gli account usati in violazione di questi termini.']],
      ['Responsabilità', ['Nei limiti consentiti dalla legge, ticly non risponde di danni derivanti dall’uso dell’app. Restano salvi i diritti che la legge riconosce ai consumatori.']],
      ['Legge applicabile', ['Si applica la legge italiana. Per i consumatori è competente il foro del luogo di residenza.']],
      ['Modifiche', [`Versione del ${TERMS_VERSION}. Le modifiche rilevanti ti saranno chieste di nuovo al prossimo accesso.`]],
    ],
  },
  en: {
    title: 'Terms of use',
    sections: [
      ['The service', [`Just-Chords is a free, open source app (GPL-3.0 licence, ${REPO}) by JustApps, a ticly project. Contact: ${CONTACT}. It is provided “as is”, with no warranty of availability or of the accuracy of chords, levels and recommendations.`]],
      ['Age', ['You must be at least 14 to create an account. By confirming it at sign-in you declare it is true.']],
      [
        'Third-party content',
        [
          'Chords and lyrics belong to their authors, publishers and transcribers. Just-Chords only opens the pages you pick, for your personal use, and keeps the link to the source.',
          'Respect the terms of the source sites and don’t republish the content. Rights holders can ask for removal through the contacts in the privacy policy.',
        ],
      ],
      ['Fair use', ['Don’t use Just-Chords in an automated or bulk way, to download large numbers of pages or to overload the service or the source sites.']],
      ['Account', ['You can delete your account at any time from Profile. We may suspend accounts used in breach of these terms.']],
      ['Liability', ['To the extent permitted by law, ticly is not liable for damages arising from the use of the app. Consumers keep the rights the law gives them.']],
      ['Governing law', ['Italian law applies. Consumers may go to the courts of their place of residence.']],
      ['Changes', [`Version of ${TERMS_VERSION}. Material changes will be asked again at your next sign-in.`]],
    ],
  },
};

const linkify = (text: string) =>
  text.split(/(https?:\/\/\S+|www\.[a-z.]+|[\w.+-]+@[\w-]+\.[a-z]{2,})/g).map((part, i) =>
    /^[\w.+-]+@/.test(part) ? (
      <a key={i} href={`mailto:${part}`}>
        {part}
      </a>
    ) : /^(https?:\/\/|www\.)/.test(part) ? (
      <a key={i} href={part.startsWith('http') ? part : `https://${part}`} target="_blank" rel="noopener noreferrer">
        {part}
      </a>
    ) : (
      part
    ),
  );

export function Legal({ doc }: { doc: 'privacy' | 'terms' | 'delete' }) {
  const { lang } = useSettings();
  const d = (doc === 'privacy' ? PRIVACY : doc === 'delete' ? DELETE : TERMS)[lang];
  return (
    <article className="legal">
      <button className="link-btn" onClick={() => (history.length > 1 ? history.back() : go('/'))}>
        ← {lang === 'it' ? 'Indietro' : 'Back'}
      </button>
      <h1>{d.title}</h1>
      {d.sections.map(([h, ps]) => (
        <section key={h}>
          <h2>{h}</h2>
          {ps.map((p, i) => (
            <p key={i}>{linkify(p)}</p>
          ))}
        </section>
      ))}
      <p className="muted small">
        <a href="#/privacy">{PRIVACY[lang].title}</a> · <a href="#/terms">{TERMS[lang].title}</a>
      </p>
    </article>
  );
}
