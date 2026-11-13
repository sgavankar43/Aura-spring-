import re
import subprocess
import os
import markdown
import pypdf

WORKSPACE_MD = "/Users/sahilgavankar/Desktop/MEGA/PROJECT_ROADMAP_AND_PROGRESS.md"
WORKSPACE_PDF = "/Users/sahilgavankar/Desktop/MEGA/PROJECT_ROADMAP_AND_PROGRESS.pdf"
ARTIFACT_MD = "/Users/sahilgavankar/.gemini/antigravity/brain/e94c3a0d-36d7-43f8-8edc-4a4391c9dba3/PROJECT_ROADMAP_AND_PROGRESS.md"
ARTIFACT_PDF = "/Users/sahilgavankar/.gemini/antigravity/brain/e94c3a0d-36d7-43f8-8edc-4a4391c9dba3/PROJECT_ROADMAP_AND_PROGRESS.pdf"
HTML_OUT = "/tmp/roadmap_print.html"

# Read Markdown
with open(WORKSPACE_MD, "r", encoding="utf-8") as f:
    raw_md = f.read()

# Copy to artifact folder
with open(ARTIFACT_MD, "w", encoding="utf-8") as f:
    f.write(raw_md)

# Convert Markdown to HTML
html_body = markdown.markdown(
    raw_md,
    extensions=['tables', 'fenced_code']
)

# Convert mermaid code blocks: <pre><code class="language-mermaid">...</code></pre> -> <div class="mermaid">...</div>
def replace_mermaid(match):
    code = match.group(1)
    code = code.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
    return f'<div class="mermaid-container"><div class="mermaid">\n{code.strip()}\n</div></div>'

html_body = re.sub(
    r'<pre><code class="language-mermaid">(.*?)</code></pre>',
    replace_mermaid,
    html_body,
    flags=re.DOTALL
)

# Style Status Badges
html_body = html_body.replace("✅ Production Live", '<span class="badge badge-success">Production Live</span>')
html_body = html_body.replace("✅ Complete", '<span class="badge badge-success">Complete</span>')
html_body = html_body.replace("✅", '<span class="checkmark">✓</span>')

# Full standalone HTML with engineering whitepaper styling
full_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Aura Platform — Development Progress & Future Roadmap</title>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
  mermaid.initialize({{
    startOnLoad: true,
    theme: 'neutral',
    themeVariables: {{
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: '11px',
      primaryColor: '#f8fafc',
      primaryBorderColor: '#cbd5e1',
      lineColor: '#64748b'
    }},
    flowchart: {{
      htmlLabels: true,
      curve: 'basis'
    }}
  }});
</script>
<style>
  @page {{
    size: A4 portrait;
    margin: 18mm 16mm 18mm 16mm;
  }}
  * {{
    box-sizing: border-box;
    -webkit-print-color-adjust: exact !important;
    print-color-adjust: exact !important;
  }}
  body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: #1e293b;
    background-color: #ffffff;
    line-height: 1.6;
    font-size: 9.5pt;
    margin: 0;
    padding: 0;
  }}
  h1 {{
    font-size: 20pt;
    font-weight: 800;
    letter-spacing: -0.03em;
    color: #0f172a;
    margin-top: 0;
    margin-bottom: 12px;
    line-height: 1.25;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 8px;
  }}
  h2 {{
    font-size: 14pt;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: #0f172a;
    margin-top: 24px;
    margin-bottom: 10px;
    border-bottom: 1px solid #e2e8f0;
    padding-bottom: 6px;
    page-break-after: avoid;
  }}
  h3 {{
    font-size: 11.5pt;
    font-weight: 600;
    color: #0f172a;
    margin-top: 18px;
    margin-bottom: 8px;
    page-break-after: avoid;
  }}
  h4 {{
    font-size: 10pt;
    font-weight: 600;
    color: #334155;
    margin-top: 14px;
    margin-bottom: 6px;
    page-break-after: avoid;
  }}
  p, ul, ol {{
    margin-top: 0;
    margin-bottom: 8px;
  }}
  ul, ol {{
    padding-left: 20px;
  }}
  li {{
    margin-bottom: 4px;
  }}
  blockquote {{
    margin: 12px 0 16px 0;
    padding: 10px 14px;
    background-color: #f8fafc;
    border-left: 4px solid #d97706;
    border-radius: 4px;
    color: #334155;
    font-size: 9pt;
  }}
  blockquote p {{
    margin: 0 0 4px 0;
  }}
  blockquote p:last-child {{
    margin: 0;
  }}
  code {{
    font-family: "SFMono-Regular", Menlo, Monaco, Consolas, "Liberation Mono", monospace;
    font-size: 8.5pt;
    background-color: #f1f5f9;
    color: #0f172a;
    padding: 2px 4px;
    border-radius: 4px;
    border: 1px solid #e2e8f0;
  }}
  pre {{
    font-family: "SFMono-Regular", Menlo, Monaco, Consolas, monospace;
    font-size: 8pt;
    background-color: #0f172a;
    color: #f8fafc;
    padding: 10px 12px;
    border-radius: 6px;
    overflow-x: auto;
    line-height: 1.4;
    margin: 12px 0;
    page-break-inside: avoid;
    white-space: pre;
  }}
  pre code {{
    background-color: transparent;
    color: inherit;
    padding: 0;
    border: none;
    font-size: inherit;
  }}
  table {{
    width: 100%;
    border-collapse: collapse;
    margin: 14px 0;
    font-size: 8.5pt;
    page-break-inside: avoid;
  }}
  th, td {{
    border: 1px solid #cbd5e1;
    padding: 7px 9px;
    text-align: left;
    vertical-align: top;
  }}
  th {{
    background-color: #f1f5f9;
    color: #0f172a;
    font-weight: 600;
  }}
  tr:nth-child(even) td {{
    background-color: #f8fafc;
  }}
  a {{
    color: #2563eb;
    text-decoration: none;
  }}
  hr {{
    border: none;
    border-top: 1px solid #e2e8f0;
    margin: 20px 0;
  }}
  .badge {{
    display: inline-block;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 7.5pt;
    font-weight: 600;
    line-height: 1.2;
    white-space: nowrap;
  }}
  .badge-success {{
    background-color: #dcfce7;
    color: #15803d;
    border: 1px solid #bbf7d0;
  }}
  .checkmark {{
    color: #16a34a;
    font-weight: bold;
    margin-left: 2px;
  }}
  .mermaid-container {{
    text-align: center;
    margin: 14px 0;
    page-break-inside: avoid;
  }}
  .mermaid {{
    display: inline-block;
    max-width: 100%;
  }}
  .mermaid svg {{
    max-width: 100%;
    height: auto;
  }}
</style>
</head>
<body>
{html_body}
</body>
</html>
"""

with open(HTML_OUT, "w", encoding="utf-8") as f:
    f.write(full_html)

# Run Chrome Headless to compile PDF
chrome_path = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
cmd = [
    chrome_path,
    "--headless=new",
    "--disable-gpu",
    "--virtual-time-budget=5000",
    "--no-pdf-header-footer",
    f"--print-to-pdf={WORKSPACE_PDF}",
    HTML_OUT
]

res = subprocess.run(cmd, capture_output=True, text=True)

# Copy to artifact folder as well
if os.path.exists(WORKSPACE_PDF):
    with open(WORKSPACE_PDF, "rb") as src, open(ARTIFACT_PDF, "wb") as dst:
        dst.write(src.read())

    reader = pypdf.PdfReader(WORKSPACE_PDF)
    print("Success! Generated updated PDF with", len(reader.pages), "pages.")
    print("File size:", os.path.getsize(WORKSPACE_PDF), "bytes")
else:
    print("Failed to generate PDF")
