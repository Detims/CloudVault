import { useEffect, useMemo, useState } from 'react'
import { apiClient } from './api'
import type { DocumentRecord, UserProfile } from './types'
import { formatBytes } from './lib/formatBytes'

const allowedTypes = new Set(['application/pdf', 'image/jpeg', 'image/png', 'text/plain'])
const maxFileBytes = 25 * 1000 * 1000

function BrandMark({ small = false }: { small?: boolean }) {
  return (
    <span className={`brand-mark${small ? ' brand-mark--small' : ''}`} aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <path d="M9 12V8.8A6.8 6.8 0 0 1 15.8 2h.4A6.8 6.8 0 0 1 23 8.8V12" />
        <rect x="5" y="11" width="22" height="18" rx="5" />
        <path d="M16 18v4" />
      </svg>
    </span>
  )
}

function FileGlyph({ type }: { type: string }) {
  const label = type === 'application/pdf' ? 'PDF' : type.startsWith('image/') ? 'IMG' : 'TXT'
  return <span className={`file-glyph file-glyph--${label.toLowerCase()}`}>{label}</span>
}

function prettyDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    .format(new Date(value))
}

export default function App() {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [documents, setDocuments] = useState<DocumentRecord[]>([])
  const [authLoading, setAuthLoading] = useState(true)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')

  useEffect(() => {
    apiClient.me()
      .then(setUser)
      .catch((problem: unknown) => {
        if (!apiClient.isUnauthenticated(problem)) setError(problem instanceof Error ? problem.message : 'Could not reach CloudVault.')
      })
      .finally(() => setAuthLoading(false))
  }, [])

  async function loadDocuments() {
    setLoading(true)
    try {
      setDocuments(await apiClient.documents())
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'Could not load your documents.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (user) void loadDocuments()
  }, [user])

  const visibleDocuments = useMemo(() => documents
    .filter((document) => document.fileName.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)), [documents, query])

  async function uploadFile() {
    if (!selectedFile) return
    setError('')
    setMessage('')
    if (!allowedTypes.has(selectedFile.type)) {
      setError('Choose a PDF, PNG, JPEG, or plain text file.')
      return
    }
    if (selectedFile.size < 1 || selectedFile.size > maxFileBytes) {
      setError('Files must be between 1 byte and 25 MB.')
      return
    }

    setBusy(true)
    try {
      const intent = await apiClient.createUpload(selectedFile)
      await apiClient.upload(selectedFile, intent)
      setSelectedFile(null)
      const input = document.getElementById('file-picker') as HTMLInputElement | null
      if (input) input.value = ''
      setMessage('Your file is safely stored.')
      await loadDocuments()
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'The upload did not finish.')
    } finally {
      setBusy(false)
    }
  }

  async function downloadFile(id: string) {
    setError('')
    try {
      const intent = await apiClient.download(id)
      window.location.assign(intent.downloadUrl)
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'Could not prepare the download.')
    }
  }

  async function deleteFile(record: DocumentRecord) {
    if (!window.confirm(`Move “${record.fileName}” out of your CloudVault?`)) return
    setError('')
    try {
      await apiClient.remove(record.id)
      setDocuments((current) => current.filter((document) => document.id !== record.id))
      setMessage('Document deleted.')
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'Could not delete this document.')
    }
  }

  async function signOut() {
    setError('')
    try {
      await apiClient.logout()
      setUser(null)
      setDocuments([])
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'Could not end the session.')
    }
  }

  const totalBytes = documents.reduce((total, document) => total + document.sizeBytes, 0)

  if (authLoading) {
    return <main className="loading-screen"><BrandMark /><span>Opening your vault…</span></main>
  }

  if (!user) {
    return (
      <main className="landing-page">
        <header className="landing-nav"><a className="brand" href="/"><BrandMark /><span>cloudvault</span></a><span className="nav-note">A quieter place for your files</span></header>
        <section className="landing-grid">
          <div className="landing-copy">
            <div className="eyebrow"><span className="eyebrow-dot" /> YOUR SPACE, YOURS ALONE</div>
            <h1>Keep the things<br />that matter <em>close.</em></h1>
            <p className="landing-description">A calm, private home for your important documents. Sign in, save what matters, and find it again when you need it.</p>
            <a className="google-button" href="/oauth2/authorization/google">
              <GoogleMark /><span>Continue with Google</span><span className="button-arrow">↗</span>
            </a>
            {error && <p className="notice notice--error" role="alert">{error}</p>}
            <p className="fine-print"><span className="mini-lock">⌑</span> Your files stay private. Only you can access them.</p>
          </div>
          <div className="vault-illustration" aria-label="Illustration of private files stored in a cloud vault">
            <div className="orbit orbit--outer" /><div className="orbit orbit--inner" />
            <div className="cloud-shape"><div className="cloud-core"><BrandMark /></div></div>
            <div className="float-card float-card--top"><FileGlyph type="application/pdf" /><span><b>Passport scan</b><small>PDF · 2.4 MB</small></span><span className="check">✓</span></div>
            <div className="float-card float-card--bottom"><span className="safe-dot" /><span><b>Private by design</b><small>Encrypted storage</small></span><span className="sparkle">✳</span></div>
            <span className="spark spark--one">✳</span><span className="spark spark--two">✦</span><span className="spark spark--three">·</span>
          </div>
        </section>
        <footer className="landing-footer"><span>Made for the things you keep.</span><span>Private storage · Simple access</span></footer>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/"><BrandMark /><span>cloudvault</span></a>
        <div className="sidebar-label">WORKSPACE</div>
        <button className="side-link side-link--active"><span className="side-icon">▤</span> My documents <span className="side-count">{documents.length}</span></button>
        <div className="sidebar-bottom">
          <div className="storage-card"><span className="storage-icon">◌</span><div><b>Your private space</b><small>Files are stored securely</small></div><span className="storage-indicator" /></div>
          <div className="user-card">
            <span className="avatar avatar--fallback">{(user.name ?? user.email ?? 'U').slice(0, 1).toUpperCase()}</span>
            <span className="user-copy"><b>{user.name ?? 'Your account'}</b><small>{user.email}</small></span>
            <button className="icon-button signout-button" onClick={() => void signOut()} aria-label="Sign out" title="Sign out">↗</button>
          </div>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar"><div className="breadcrumb"><span>Workspace</span><span className="crumb-slash">/</span><b>My documents</b></div><div className="topbar-right"><span className="secure-label"><i /> Private space</span><button className="top-user" onClick={() => void signOut()} title="Sign out">{(user.name ?? 'U').slice(0, 1).toUpperCase()}</button></div></header>
        <div className="content-wrap">
          <div className="page-heading"><div><div className="eyebrow eyebrow--small">YOUR LIBRARY</div><h1>My documents<span className="title-period">.</span></h1><p>A safe place for the files you want to keep close.</p></div><button className="button button--dark" onClick={() => document.getElementById('file-picker')?.click()}><span className="plus">＋</span> Add a document</button></div>

          <div className="summary-row">
            <div className="summary-card"><span className="summary-icon summary-icon--files">▤</span><div><small>DOCUMENTS</small><b>{documents.length}</b></div><span className="summary-detail">{documents.length === 1 ? 'file in your vault' : 'files in your vault'}</span></div>
            <div className="summary-card"><span className="summary-icon summary-icon--size">◷</span><div><small>SPACE USED</small><b>{formatBytes(totalBytes)}</b></div><span className="summary-detail">Private object storage</span></div>
            <div className="summary-card summary-card--security"><span className="summary-icon summary-icon--secure">⌑</span><div><small>VAULT STATUS</small><b>Just yours</b></div><span className="status-pill"><i /> Protected</span></div>
          </div>

          <section className="documents-panel">
            <div className="panel-heading"><div><h2>All files <span>{documents.length}</span></h2><p>Your personal collection, all in one place.</p></div><label className="search-box"><span>⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search files" aria-label="Search files" /></label></div>
            <input id="file-picker" className="visually-hidden" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt" onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)} />
            {selectedFile && <div className="selected-file"><FileGlyph type={selectedFile.type} /><span><b>{selectedFile.name}</b><small>{formatBytes(selectedFile.size)} · {selectedFile.type || 'Unknown type'}</small></span><button className="icon-button" onClick={() => setSelectedFile(null)} aria-label="Remove selected file">×</button><button className="button button--small" onClick={() => void uploadFile()} disabled={busy}>{busy ? 'Uploading…' : 'Upload file'}</button></div>}
            {error && <div className="notice notice--error" role="alert">{error}<button onClick={() => setError('')} aria-label="Dismiss error">×</button></div>}
            {message && <div className="notice notice--success" role="status">{message}<button onClick={() => setMessage('')} aria-label="Dismiss message">×</button></div>}
            <div className="table-wrap">
              <table>
                <thead><tr><th>NAME</th><th>STATUS</th><th>DATE ADDED</th><th>FILE SIZE</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
                <tbody>
                  {visibleDocuments.map((record) => (
                    <tr key={record.id}>
                      <td><div className="file-cell"><FileGlyph type={record.contentType} /><span><b>{record.fileName}</b><small>{record.contentType}</small></span></div></td>
                      <td><span className={`file-status ${record.status === 'AVAILABLE' ? '' : 'file-status--pending'}`}><i />{record.status === 'AVAILABLE' ? 'Stored' : 'Upload pending'}</span></td>
                      <td className="muted-cell">{prettyDate(record.createdAt)}</td>
                      <td className="muted-cell">{formatBytes(record.sizeBytes)}</td>
                      <td><div className="row-actions"><button className="icon-button" disabled={record.status !== 'AVAILABLE'} onClick={() => void downloadFile(record.id)} aria-label={`Download ${record.fileName}`} title="Download">↓</button><button className="icon-button" onClick={() => void deleteFile(record)} aria-label={`Delete ${record.fileName}`} title="Delete">⌫</button></div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {loading && <div className="table-state">Loading your documents…</div>}
              {!loading && visibleDocuments.length === 0 && (
                <div className="empty-state"><span className="empty-art"><BrandMark /></span><h3>{query ? 'No matching files' : 'Your vault is ready.'}</h3><p>{query ? 'Try another name or clear your search.' : 'Add your first document and keep it somewhere safe.'}</p>{!query && <button className="button button--outline" onClick={() => document.getElementById('file-picker')?.click()}>Choose a file <span>＋</span></button>}</div>
              )}
            </div>
            <div className="panel-foot"><span><i /> Your files are private to your account</span><span>PDF, PNG, JPEG, TXT · Up to 25 MB</span></div>
          </section>
          <footer className="app-footer"><span>CloudVault</span><span>Keep what matters close.</span></footer>
        </div>
      </section>
    </main>
  )
}

function GoogleMark() {
  return <svg className="google-mark" viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.23c0-.72-.06-1.42-.18-2.1H12v3.98h5.38a4.6 4.6 0 0 1-2 3.02v2.46h3.24c1.9-1.75 2.98-4.32 2.98-7.36Z" /><path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.42l-3.24-2.46c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.6-4.13H3.06v2.54A10 10 0 0 0 12 22Z" /><path fill="#FBBC05" d="M6.4 13.95a6 6 0 0 1 0-3.9V7.5H3.06a10 10 0 0 0 0 9l3.34-2.55Z" /><path fill="#EA4335" d="M12 5.92c1.48 0 2.8.5 3.84 1.5l2.88-2.88C16.96 2.96 14.7 2 12 2a10 10 0 0 0-8.94 5.5L6.4 10.05c.8-2.37 3-4.13 5.6-4.13Z" /></svg>
}
