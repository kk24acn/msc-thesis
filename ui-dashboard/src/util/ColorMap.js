export const statusColorMap = {
    active: 'bg-darcula-success',
    inactive: 'bg-darcula-muted',
    warning: 'bg-darcula-warning',
};

export const stageBadgeClasses = {
    completed: 'bg-darcula-success text-darcula-contrast',
    running: 'bg-darcula-cyan text-darcula-contrast',
    in_mempool: 'bg-darcula-purple text-darcula-contrast',
    stalled: 'bg-darcula-warning text-darcula-contrast',
    sweeped: 'bg-darcula-warning text-darcula-contrast',
    warning: 'bg-darcula-warning text-darcula-contrast',
    failed: 'bg-darcula-accent text-darcula-contrast',
    crypto_abort: 'bg-darcula-accent text-darcula-contrast',
    verification_abort: 'bg-darcula-warning text-darcula-contrast',
    pending: 'bg-darcula-border text-darcula-muted',
};

export const getSafeStatus = (status) => {
    return ['completed', 'running', 'failed', 'crypto_abort', 'verification_abort', 'in_mempool', 'stalled', 'sweeped', 'warning'].includes(status) ? status : 'pending';
};