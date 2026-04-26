export const statusColorMap = {
    active: 'bg-darcula-success',
    inactive: 'bg-darcula-muted',
    warning: 'bg-darcula-warning',
};

export const stageBadgeClasses = {
    completed: 'bg-darcula-success text-darcula-contrast',
    running: 'bg-darcula-cyan text-darcula-contrast',
    failed: 'bg-darcula-accent text-darcula-contrast',
    pending: 'bg-darcula-border text-darcula-muted',
};

export const getSafeStatus = (status) => {
    return ['completed', 'running', 'failed'].includes(status) ? status : 'pending';
};