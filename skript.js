// === ТЕСТОВИЙ КОД ===
document.addEventListener('DOMContentLoaded', function() {
    console.log('Скрипт завантажено!');

    const incomeInput = document.getElementById('income');
    const expensesInput = document.getElementById('expenses');
    const taxRateInput = document.getElementById('taxRate');
    const reserveMonthsInput = document.getElementById('reserveMonths');

    const netProfitEl = document.getElementById('netProfit');
    const breakEvenEl = document.getElementById('breakEven');
    const safetyMarginEl = document.getElementById('safetyMargin');
    const statusEl = document.getElementById('status');

    function updateResults() {
        const income = parseFloat(incomeInput.value) || 0;
        const expenses = parseFloat(expensesInput.value) || 0;
        const taxRate = parseFloat(taxRateInput.value) || 0;
        const reserveMonths = parseFloat(reserveMonthsInput.value) || 3;

        const netProfit = income - expenses - (income * taxRate / 100);
        const breakEven = expenses * 1.3; // спрощено
        const safetyMargin = (income - expenses) * reserveMonths;

        netProfitEl.textContent = Math.round(netProfit).toLocaleString('uk-UA');
        breakEvenEl.textContent = Math.round(breakEven).toLocaleString('uk-UA');
        safetyMarginEl.textContent = Math.round(safetyMargin).toLocaleString('uk-UA');

        let statusText = '';
        let statusClass = '';
        if (netProfit <= 0) {
            statusText = '🔴 Збитки!';
            statusClass = 'red';
        } else if (netProfit / income * 100 < 10) {
            statusText = '🟡 Низька рентабельність';
            statusClass = 'yellow';
        } else {
            statusText = '🟢 Стабільно';
            statusClass = 'green';
        }
        statusEl.textContent = statusText;
        statusEl.className = 'result-value ' + statusClass;
    }

    [incomeInput, expensesInput, taxRateInput, reserveMonthsInput].forEach(input => {
        input.addEventListener('input', updateResults);
    });

    updateResults();
});
