const historial = [];

async function cargarCuentas() {
    try {
        const [nacional, internacional] = await Promise.all([
            fetch('/api/cuentas/nacional').then(r => r.json()),
            fetch('/api/cuentas/internacional').then(r => r.json())
        ]);

        renderCuentas('cuentasNacional', nacional, 'BN');
        renderCuentas('cuentasInternacional', internacional, 'BI');
        poblarSelects(nacional, internacional);
        await cargarEstadoFallo();
    } catch (e) {
        console.error('Error cargando cuentas:', e);
    }
}

function renderCuentas(containerId, cuentas, prefix) {
    const el = document.getElementById(containerId);
    el.innerHTML = cuentas.map(c => `
            <div class="account-card">
                <div class="account-num">${c.numeroCuenta}</div>
                <div class="account-name">${c.titular}</div>
                <div class="account-balance">$${Number(c.saldo).toLocaleString('es-CO', {minimumFractionDigits:2})} </div>
            </div>
        `).join('');
}

function poblarSelects(nacional, internacional) {
    const sel1 = document.getElementById('cuentaOrigen');
    const sel2 = document.getElementById('cuentaDestino');
    sel1.innerHTML = nacional.map(c =>
        `<option value="${c.numeroCuenta}">${c.numeroCuenta} - ${c.titular} ($${Number(c.saldo).toLocaleString()})</option>`
    ).join('');
    sel2.innerHTML = internacional.map(c =>
        `<option value="${c.numeroCuenta}">${c.numeroCuenta} - ${c.titular} ($${Number(c.saldo).toLocaleString()})</option>`
    ).join('');
}

async function realizarTransferencia() {
    const cuentaOrigen  = document.getElementById('cuentaOrigen').value;
    const cuentaDestino = document.getElementById('cuentaDestino').value;
    const monto         = parseFloat(document.getElementById('monto').value);
    const btn           = document.getElementById('btnTransferir');

    if (!cuentaOrigen || !cuentaDestino || !monto || monto <= 0) {
        alert('Por favor completa todos los campos con valores válidos.');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Ejecutando SAGA...';

    try {
        const res = await fetch('/api/transferencias', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ cuentaOrigen, cuentaDestino, monto })
        });
        const data = await res.json();
        mostrarResultado(data);
        agregarHistorial(data);
        await cargarCuentas(); // refrescar saldos
    } catch (e) {
        mostrarResultado({ estado: 'FALLIDA', mensaje: 'Error de conexión: ' + e.message });
    } finally {
        btn.disabled = false;
        btn.textContent = 'Ejecutar Transferencia SAGA';
    }
}

function mostrarResultado(data) {
    const el = document.getElementById('resultado');
    const iconos = { COMPLETADA: 'ok', REVERTIDA: 'revertida', FALLIDA: 'fallida' };
    el.className = `result ${data.estado}`;
    el.style.display = 'block';
    el.innerHTML = `
            <div class="result-title">${iconos[data.estado] || '?'} ${data.estado}</div>
            ${data.referencia ? `<div class="result-ref">Ref: ${data.referencia}</div>` : ''}
            <div class="result-detail">${data.mensaje || ''}</div>
        `;
}

function agregarHistorial(data) {
    historial.unshift(data);
    const el = document.getElementById('historial');
    el.innerHTML = historial.map(t => `
            <div class="history-item">
                <div>
                    <span class="result-ref">${t.referencia || 'N/A'}</span>
                    <span style="color:#94a3b8; margin:0 8px">·</span>
                    <span>${t.cuentaOrigen || '?'} → ${t.cuentaDestino || '?'}</span>
                    <span style="color:#94a3b8; margin:0 8px">·</span>
                    <span style="color:#22c55e">$${Number(t.monto||0).toLocaleString()}</span>
                </div>
                <span class="estado-badge estado-${t.estado}">${t.estado}</span>
            </div>
        `).join('');
}





// Cargar al inicio
cargarCuentas();
// Refrescar cada 30 segundos
setInterval(cargarCuentas, 30000);
