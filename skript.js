// --- DOM References ---
const incomeInput = document.getElementById('income');
const expensesInput = document.getElementById('expenses');
const taxRateInput = document.getElementById('taxRate');
const reserveMonthsInput = document.getElementById('reserveMonths');

const netProfitEl = document.getElementById('netProfit');
const breakEvenEl = document.getElementById('breakEven');
const safetyMarginEl = document.getElementById('safetyMargin');
const statusEl = document.getElementById('status');

// --- Core Logic ---
function calcNetProfit(income, expenses, taxRate) {
    const tax = income * (taxRate / 100);
    return income - expenses - tax;
}

function calcBreakEven(fixedCosts, variableCostPercent = 0.3) {
    // Припускаємо, що змінні витрати = 30% від доходу
    // Точка беззбитковості = постійні витрати / (1 - змінні витрати у %)
    const variableCosts = fixedCosts * variableCostPercent;
    return fixedCosts + variableCosts;
}

function calcSafetyMargin(monthlyIncome, monthlyExpenses, reserveMonths) {
    const monthlyNet = monthlyIncome - monthlyExpenses;
    return monthlyNet * reserveMonths;
}

function getStatus(netProfit, income) {
    const profitMargin = income > 0 ? (netProfit / income) * 100 : 0;
    
    if (netProfit <= 0) {
        return { text: '🔴 Збитки! Терміново змінюй стратегію', className: 'red' };
    } else if (profitMargin < 10) {
        return { text: '🟡 Критично низька рентабельність (<10%)', className: 'yellow' };
    } else if (profitMargin < 25) {
        return { text: '🟢 Стабільно, але є потенціал зростання', className: 'green' };
    } else {
        return { text: '✅ Відмінно! Висока рентабельність', className: 'green' };
    }
}

// --- Format Number ---
function formatNumber(num) {
    return Math.round(num).toLocaleString('uk-UA');
}

// --- Update All Results ---
function updateResults() {
    const income = parseFloat(incomeInput.value) || 0;
    const expenses = parseFloat(expensesInput.value) || 0;
    const taxRate = parseFloat(taxRateInput.value) || 0;
    const reserveMonths = parseFloat(reserveMonthsInput.value) || 3;

    const netProfit = calcNetProfit(income, expenses, taxRate);
    const breakEven = calcBreakEven(expenses);
    const safetyMargin = calcSafetyMargin(income, expenses, reserveMonths);
    const status = getStatus(netProfit, income);

    // Анімація зміни цифр
    animateNumber(netProfitEl, netProfit);
    animateNumber(breakEvenEl, breakEven);
    animateNumber(safetyMarginEl, safetyMargin);

    // Статус
    statusEl.textContent = status.text;
    statusEl.className = 'result-value ' + status.className;

    // Збереження в LocalStorage
    saveToLocalStorage({ income, expenses, taxRate, reserveMonths });
}

// --- Number Animation (лічильник) ---
function animateNumber(el, target) {
    const current = parseInt(el.textContent.replace(/\s/g, '')) || 0;
    const duration = 400;
    const startTime = performance.now();

    function step(currentTime) {
        const progress = Math.min((currentTime - startTime) / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic
        const currentVal = Math.round(current + (target - current) * eased);
        el.textContent = formatNumber(currentVal);

        if (progress < 1) {
            requestAnimationFrame(step);
        } else {
            el.textContent = formatNumber(target);
        }
    }
    requestAnimationFrame(step);
}

// --- LocalStorage ---
function saveToLocalStorage(data) {
    try {
        localStorage.setItem('financeCalcData', JSON.stringify(data));
    } catch (e) {
        // Ignore
    }
}

function loadFromLocalStorage() {
    try {
        const data = JSON.parse(localStorage.getItem('financeCalcData'));
        if (data) {
            incomeInput.value = data.income || 50000;
            expensesInput.value = data.expenses || 30000;
            taxRateInput.value = data.taxRate || 5;
            reserveMonthsInput.value = data.reserveMonths || 3;
        }
    } catch (e) {
        // Ignore
    }
}

// --- Export PDF ---
function exportPDF() {
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();

    doc.setFontSize(18);
    doc.text('Фінансовий звіт ФОП', 20, 30);

    doc.setFontSize(12);
    const income = parseFloat(incomeInput.value) || 0;
    const expenses = parseFloat(expensesInput.value) || 0;
    const taxRate = parseFloat(taxRateInput.value) || 0;
    const reserveMonths = parseFloat(reserveMonthsInput.value) || 3;

    const netProfit = calcNetProfit(income, expenses, taxRate);
    const breakEven = calcBreakEven(expenses);
    const safetyMargin = calcSafetyMargin(income, expenses, reserveMonths);
    const status = getStatus(netProfit, income);

    const lines = [
        `Дохід: ${formatNumber(income)} грн`,
        `Витрати: ${formatNumber(expenses)} грн`,
        `Ставка податку: ${taxRate}%`,
        `Запас міцності: ${reserveMonths} міс.`,
        ``,
        `Чистий прибуток: ${formatNumber(netProfit)} грн`,
        `Точка беззбитковості: ${formatNumber(breakEven)} грн`,
        `Запас міцності (подушка): ${formatNumber(safetyMargin)} грн`,
        ``,
        `Статус: ${status.text}`
    ];

    let y = 50;
    lines.forEach(line => {
        doc.text(line, 20, y);
        y += 10;
    });

    doc.save('financial_report.pdf');
}

// --- Event Listeners ---
[incomeInput, expensesInput, taxRateInput, reserveMonthsInput].forEach(input => {
    input.addEventListener('input', updateResults);
});

document.getElementById('exportPDF').addEventListener('click', exportPDF);

// --- Init ---
loadFromLocalStorage();
updateResults();

// Автозбереження при закритті сторінки
window.addEventListener('beforeunload', () => {
    const income = parseFloat(incomeInput.value) || 0;
    const expenses = parseFloat(expensesInput.value) || 0;
    const taxRate = parseFloat(taxRateInput.value) || 0;
    const reserveMonths = parseFloat(reserveMonthsInput.value) || 3;
    saveToLocalStorage({ income, expenses, taxRate, reserveMonths });
});
